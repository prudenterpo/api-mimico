package com.rpo.mimico.services;

import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoundTimerServiceTest {

    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private GameplayService gameplayService;
    @Mock
    private TimerService timerService;

    private RoundTimerService service;

    @BeforeEach
    void setUp() {
        when(timerService.now()).thenReturn(Instant.parse("2026-09-26T12:00:00Z"));
        service = new RoundTimerService(matchStateRepository, gameplayService, timerService);
    }

    @Test
    void scheduledTimerIgnoresPausedMatch() {
        MatchEntity match = new MatchEntity();
        match.setId(UUID.randomUUID());
        match.setMatchStatus(MatchStatus.MATCH_PAUSED);
        MatchStateEntity state = new MatchStateEntity();
        state.setMatch(match);
        state.setIsPaused(true);
        state.setRoundState(RoundState.ROUND_GUESSING);
        when(matchStateRepository.findExpiredRounds(any(), eq(RoundState.ROUND_GUESSING), eq(MatchStatus.MATCH_ACTIVE)))
                .thenReturn(List.of(state));

        service.checkExpiredRounds();

        verify(gameplayService, never()).handleTimeout(any());
    }

    @Test
    void scheduledTimerResolvesActiveExpiredRound() {
        UUID matchId = UUID.randomUUID();
        MatchEntity match = new MatchEntity();
        match.setId(matchId);
        match.setMatchStatus(MatchStatus.MATCH_ACTIVE);
        MatchStateEntity state = new MatchStateEntity();
        state.setMatch(match);
        state.setIsPaused(false);
        state.setRoundState(RoundState.ROUND_GUESSING);
        when(matchStateRepository.findExpiredRounds(any(), eq(RoundState.ROUND_GUESSING), eq(MatchStatus.MATCH_ACTIVE)))
                .thenReturn(List.of(state));

        service.checkExpiredRounds();

        verify(gameplayService).handleTimeout(matchId);
    }
}
