package com.rpo.mimico.services;

import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchStateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoundTimerService {

    private final MatchStateRepository matchStateRepository;
    private final GameplayService gameplayService;
    private final TimerService timerService;

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void checkExpiredRounds() {
        LocalDateTime now = GameClock.toLocalDateTime(timerService.now());
        List<MatchStateEntity> expiredRounds = matchStateRepository.findExpiredRounds(
                now,
                RoundState.ROUND_GUESSING,
                MatchStatus.MATCH_ACTIVE
        );

        if (expiredRounds.isEmpty()) {
            return;
        }

        log.info("Found {} expired rounds to process", expiredRounds.size());

        for (MatchStateEntity matchState : expiredRounds) {
            if (shouldIgnore(matchState)) {
                continue;
            }
            try {
                gameplayService.handleTimeout(matchState.getMatch().getId());
            } catch (Exception e) {
                log.error("Failed to process expired round: matchId={}, error={}",
                        matchState.getMatch().getId(), e.getMessage(), e);
            }
        }
    }

    private boolean shouldIgnore(MatchStateEntity matchState) {
        if (Boolean.TRUE.equals(matchState.getIsPaused())) {
            return true;
        }
        if (matchState.getMatch().getMatchStatus() == MatchStatus.MATCH_PAUSED
                || matchState.getMatch().getMatchStatus() == MatchStatus.MATCH_FINISHED) {
            return true;
        }
        return matchState.getRoundState() != RoundState.ROUND_GUESSING;
    }
}
