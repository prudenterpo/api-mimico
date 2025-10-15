package com.rpo.mimico.services;

import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchStateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoundTimerService {

    private final MatchStateRepository matchStateRepository;
    private final GameplayService gameplayService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void checkExpiredRounds() {
        LocalDateTime now = LocalDateTime.now();

        List<MatchStateEntity> expiredRounds = matchStateRepository.findExpiredRounds(now);

        if (expiredRounds.isEmpty()) return;

        log.info("Found {} expired rounds to process", expiredRounds.size());

        for (MatchStateEntity matchState : expiredRounds) {
            try {
                processExpiredRound(matchState);
            } catch (Exception e) {
                log.error("Failed to process expired round: matchId={}, error={}",
                        matchState.getMatch().getId(), e.getMessage(), e);
            }
        }
    }

    private void processExpiredRound(MatchStateEntity matchState) {
        UUID matchId = matchState.getMatch().getId();
        Character teamThatTimeOut = matchState.getCurrentTeam();

        log.info("Processing round timeout: matchId={}, team={}, expiredAt={}",
                matchId, teamThatTimeOut, matchState.getRoundExpiresAt());

        gameplayService.handleTimeout(matchId);

        broadcastTimeoutEvent(matchId, teamThatTimeOut);

        log.info("Round timeout processed successfully: matchId={}", matchId);
    }

    private void broadcastTimeoutEvent(UUID matchId, Character teamThatTimeOut) {
        Map<String, Object> payload = Map.of(
                "type", "ROUND_TIMEOUT",
                "teamThatTimeOut", teamThatTimeOut,
                "timestamp", LocalDateTime.now().toString()
        );

        String destination = "/topic/match/" + matchId + "/timeout";
        messagingTemplate.convertAndSend(destination, payload);

        log.debug("Timeout event broadcasted: matchId={}, team={}", matchId, teamThatTimeOut);
    }
}
