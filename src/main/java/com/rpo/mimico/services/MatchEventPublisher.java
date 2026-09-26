package com.rpo.mimico.services;

import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.dtos.MatchStateResponseDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.WordCardResponseDTO;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MatchStateRepository matchStateRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchStateMapper matchStateMapper = new MatchStateMapper();

    public void publishState(UUID matchId) {
        MatchStateEntity state = matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
        MatchEntity match = state.getMatch();
        List<MatchPlayerEntity> players = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId);
        MatchStateResponseDTO dto = matchStateMapper.toDto(match, state, players);
        messagingTemplate.convertAndSend(
                "/topic/match/" + matchId + "/state",
                new RealtimeEventEnvelopeDTO<>("MATCH_STATE_UPDATED", dto, OffsetDateTime.now())
        );
    }

    public void publishEnded(MatchEntity match) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", match.getId());
        data.put("tableId", match.getTable().getId());
        data.put("winnerTeam", match.getWinnerTeam() == null ? null : match.getWinnerTeam().toString());
        data.put("finishReason", match.getFinishReason() == null ? FinishReason.BOARD_WIN.name() : match.getFinishReason().name());
        messagingTemplate.convertAndSend(
                "/topic/table/" + match.getTable().getId() + "/closed",
                new RealtimeEventEnvelopeDTO<>("MATCH_ENDED", data, OffsetDateTime.now())
        );
    }

    public void publishWordCard(UUID mimeUserId, UUID matchId, List<WordCardResponseDTO.WordOption> words) {
        List<Map<String, Object>> card = words.stream()
                .map(word -> Map.<String, Object>of(
                        "wordId", word.wordId(),
                        "text", word.text(),
                        "category", word.category()
                ))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", matchId);
        data.put("wordCard", card);
        messagingTemplate.convertAndSendToUser(
                mimeUserId.toString(),
                "/queue/match/" + matchId + "/word-card",
                new RealtimeEventEnvelopeDTO<>("WORD_CARD_DRAWN", data, OffsetDateTime.now())
        );
    }
}
