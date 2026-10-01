package com.rpo.mimico.services;

import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.PauseReason;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchMediaServiceTest {

    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private TimerService timerService;
    @Mock
    private MatchEventPublisher matchEventPublisher;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private GameplayService gameplayService;

    private MatchMediaService service;
    private UUID matchId;
    private UserEntity mime;
    private MatchEntity match;
    private MatchStateEntity state;

    @BeforeEach
    void setUp() {
        service = new MatchMediaService(
                matchStateRepository,
                matchRepository,
                matchPlayerRepository,
                timerService,
                matchEventPublisher,
                messagingTemplate,
                gameplayService
        );
        when(timerService.now()).thenReturn(Instant.parse("2026-09-26T12:00:00Z"));
        matchId = UUID.randomUUID();
        mime = new UserEntity();
        mime.setId(UUID.randomUUID());
        mime.setNickname("mime");

        match = new MatchEntity();
        match.setId(matchId);
        match.setMatchStatus(MatchStatus.MATCH_ACTIVE);

        state = new MatchStateEntity();
        state.setMatch(match);
        state.setCurrentMimePlayer(mime);
        state.setCurrentTeam('A');
        state.setIsPaused(false);
        state.setRoundState(RoundState.ROUND_GUESSING);
        state.setRoundExpiresAt(GameClock.toLocalDateTime(Instant.parse("2026-09-26T12:00:00Z")).plusSeconds(40));

        MatchPlayerEntity mimePlayer = new MatchPlayerEntity();
        mimePlayer.setUser(mime);
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(state));
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, mime.getId())).thenReturn(Optional.of(mimePlayer));
    }

    @Test
    void mimeUnavailablePausesWithoutDisconnectOrForfeit() {
        service.reportUnavailable(matchId, mime.getId());

        assertTrue(state.getIsPaused());
        assertEquals(PauseReason.MIME_MEDIA_FAILED, state.getPauseReason());
        assertNull(state.getDisconnectedUser());
        assertNull(state.getReconnectDeadline());
        assertEquals(40, state.getRemainingRoundSecondsOnPause());
        assertEquals(MatchStatus.MATCH_PAUSED, match.getMatchStatus());
        assertNull(match.getFinishReason());
        assertNull(match.getFinishedAt());
        verify(matchStateRepository).save(state);
        verify(matchRepository).save(match);
        verify(messagingTemplate).convertAndSend(eq("/topic/match/" + matchId + "/paused"), any(Object.class));
        verify(matchEventPublisher).publishState(matchId);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/table/" + matchId + "/closed"), any(Object.class));
        verify(gameplayService, never()).handleTimeout(any());
    }

    @Test
    void secondUnavailableDoesNotStoreAnotherPause() {
        service.reportUnavailable(matchId, mime.getId());
        LocalDateTime firstPausedAt = state.getPausedAt();

        service.reportUnavailable(matchId, mime.getId());

        assertEquals(PauseReason.MIME_MEDIA_FAILED, state.getPauseReason());
        assertEquals(firstPausedAt, state.getPausedAt());
        assertNull(state.getDisconnectedUser());
        assertNull(state.getReconnectDeadline());
        verify(matchStateRepository, times(1)).save(any());
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(Object.class));
        verify(matchEventPublisher, times(1)).publishState(matchId);
    }

    @Test
    void mimeAvailableResumesFromRemainingDurationWhenNobodyIsDisconnected() {
        state.setIsPaused(true);
        state.setPauseReason(PauseReason.MIME_MEDIA_FAILED);
        state.setRemainingRoundSecondsOnPause(25);
        state.setDisconnectedUser(null);
        state.setReconnectDeadline(null);
        match.setMatchStatus(MatchStatus.MATCH_PAUSED);
        when(timerService.now()).thenReturn(Instant.parse("2026-09-26T12:00:15Z"));

        service.reportAvailable(matchId, mime.getId());

        assertFalse(state.getIsPaused());
        assertNull(state.getPauseReason());
        assertNull(state.getRemainingRoundSecondsOnPause());
        assertNull(state.getDisconnectedUser());
        assertNull(state.getReconnectDeadline());
        assertEquals(
                GameClock.toLocalDateTime(Instant.parse("2026-09-26T12:00:15Z")).plusSeconds(25),
                state.getRoundExpiresAt()
        );
        assertEquals(MatchStatus.MATCH_ACTIVE, match.getMatchStatus());
        verify(messagingTemplate).convertAndSend(eq("/topic/match/" + matchId + "/resumed"), any(Object.class));
        verify(matchEventPublisher).publishState(matchId);
        verify(gameplayService, never()).handleTimeout(any());
    }

    @Test
    void mediaAvailableDoesNotResumeADisconnectPauseOrChangeTheDeadline() {
        LocalDateTime deadline = GameClock.toLocalDateTime(Instant.parse("2026-09-26T12:00:00Z")).plusSeconds(60);
        UserEntity disconnected = new UserEntity();
        disconnected.setId(UUID.randomUUID());
        state.setIsPaused(true);
        state.setPauseReason(PauseReason.PLAYER_DISCONNECTED);
        state.setDisconnectedUser(disconnected);
        state.setReconnectDeadline(deadline);
        state.setRemainingRoundSecondsOnPause(25);
        match.setMatchStatus(MatchStatus.MATCH_PAUSED);
        match.setFinishReason(null);

        service.reportAvailable(matchId, mime.getId());

        assertTrue(state.getIsPaused());
        assertEquals(PauseReason.PLAYER_DISCONNECTED, state.getPauseReason());
        assertEquals(deadline, state.getReconnectDeadline());
        assertEquals(disconnected.getId(), state.getDisconnectedUser().getId());
        assertEquals(25, state.getRemainingRoundSecondsOnPause());
        assertEquals(MatchStatus.MATCH_PAUSED, match.getMatchStatus());
        assertNull(match.getFinishReason());
        verify(matchStateRepository, never()).save(any());
        verify(matchRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(gameplayService, never()).handleTimeout(any());
    }

    @Test
    void resumedRoundWithNoTimeLeftTimesOut() {
        state.setIsPaused(true);
        state.setPauseReason(PauseReason.MIME_MEDIA_FAILED);
        state.setRemainingRoundSecondsOnPause(0);
        state.setRoundState(RoundState.ROUND_GUESSING);
        match.setMatchStatus(MatchStatus.MATCH_PAUSED);

        service.reportAvailable(matchId, mime.getId());

        assertFalse(state.getIsPaused());
        assertEquals(GameClock.toLocalDateTime(Instant.parse("2026-09-26T12:00:00Z")), state.getRoundExpiresAt());
        verify(gameplayService).handleTimeout(matchId);
    }
}
