package com.rpo.mimico.services;

import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.WordCardResponseDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;

    @Test
    void publishesMatchStateUpdatedAndPrivateWordCardAndMatchEnded() {
        UUID matchId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setNickname("mime");

        GameTableEntity table = new GameTableEntity();
        table.setId(tableId);
        MatchEntity match = new MatchEntity();
        match.setId(matchId);
        match.setTable(table);
        match.setMatchStatus(MatchStatus.MATCH_ACTIVE);
        match.setWinnerTeam('A');
        match.setFinishReason(FinishReason.BOARD_WIN);

        MatchStateEntity state = new MatchStateEntity();
        state.setMatch(match);
        state.setTeamAPosition(11);
        state.setTeamBPosition(0);
        state.setCurrentTeam('A');
        state.setCurrentMimePlayer(user);
        state.setRoundState(RoundState.ROUND_GUESSING);
        state.setIsPaused(false);

        MatchPlayerEntity player = new MatchPlayerEntity();
        player.setUser(user);
        player.setNickname("mime");
        player.setTeam('A');
        player.setPlayerOrder(1);

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(state));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId)).thenReturn(List.of(player));

        MatchEventPublisher publisher = new MatchEventPublisher(messagingTemplate, matchStateRepository, matchPlayerRepository);
        publisher.publishState(matchId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEventEnvelopeDTO<Object>> stateCaptor = ArgumentCaptor.forClass(RealtimeEventEnvelopeDTO.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/match/" + matchId + "/state"),
                stateCaptor.capture()
        );
        assertEquals("MATCH_STATE_UPDATED", stateCaptor.getValue().type());

        UUID wordId = UUID.randomUUID();
        publisher.publishWordCard(userId, matchId, List.of(new WordCardResponseDTO.WordOption(wordId, "gato", "EU_SOU")));
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(userId.toString()),
                org.mockito.ArgumentMatchers.eq("/queue/match/" + matchId + "/word-card"),
                stateCaptor.capture()
        );
        assertEquals("WORD_CARD_DRAWN", stateCaptor.getValue().type());

        publisher.publishEnded(match);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/table/" + tableId + "/closed"),
                stateCaptor.capture()
        );
        assertEquals("MATCH_ENDED", stateCaptor.getValue().type());
    }
}
