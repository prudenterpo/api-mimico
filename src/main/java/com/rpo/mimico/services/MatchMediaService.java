package com.rpo.mimico.services;

import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.PauseReason;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchMediaService {

    private final MatchStateRepository matchStateRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final TimerService timerService;
    private final MatchEventPublisher matchEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameplayService gameplayService;

    @Transactional
    public void reportUnavailable(UUID matchId, UUID userId) {
        MatchStateEntity state = getState(matchId);
        MatchEntity match = state.getMatch();
        if (match.getFinishedAt() != null || match.getMatchStatus() == MatchStatus.MATCH_FINISHED) {
            throw new IllegalStateException("match already finished");
        }
        requireMember(matchId, userId);
        requireMime(state, userId);

        if (Boolean.TRUE.equals(state.getIsPaused())) {
            if (state.getPauseReason() == PauseReason.MIME_MEDIA_FAILED) {
                log.info("Mime media already unavailable: match={}, user={}", matchId, userId);
            }
            return;
        }
        if (match.getMatchStatus() != MatchStatus.MATCH_ACTIVE) {
            throw new IllegalStateException("match not active");
        }

        LocalDateTime now = GameClock.toLocalDateTime(timerService.now());
        if (state.getRoundExpiresAt() != null) {
            long seconds = Duration.between(now, state.getRoundExpiresAt()).getSeconds();
            state.setRemainingRoundSecondsOnPause((int) Math.max(seconds, 0));
        }
        state.setIsPaused(true);
        state.setPausedAt(now);
        state.setPauseReason(PauseReason.MIME_MEDIA_FAILED);
        state.setDisconnectedUser(null);
        state.setReconnectDeadline(null);
        match.setMatchStatus(MatchStatus.MATCH_PAUSED);
        matchRepository.save(match);
        matchStateRepository.save(state);

        log.info("Match paused for mime media failure: match={}, user={}", matchId, userId);
        broadcastPaused(matchId);
        matchEventPublisher.publishState(matchId);
    }

    @Transactional
    public void reportAvailable(UUID matchId, UUID userId) {
        MatchStateEntity state = getState(matchId);
        requireMember(matchId, userId);
        requireMime(state, userId);

        if (state.getPauseReason() == PauseReason.PLAYER_DISCONNECTED) {
            log.info("Media available ignored during disconnect pause: match={}, user={}", matchId, userId);
            return;
        }
        if (!Boolean.TRUE.equals(state.getIsPaused()) || state.getPauseReason() != PauseReason.MIME_MEDIA_FAILED) {
            return;
        }
        if (state.getDisconnectedUser() != null) {
            return;
        }

        Integer remaining = state.getRemainingRoundSecondsOnPause();
        LocalDateTime now = GameClock.toLocalDateTime(timerService.now());
        state.setIsPaused(false);
        state.setPausedAt(null);
        state.setPauseReason(null);
        state.setDisconnectedUser(null);
        state.setReconnectDeadline(null);
        state.setRemainingRoundSecondsOnPause(null);
        MatchEntity match = state.getMatch();
        if (match.getMatchStatus() == MatchStatus.MATCH_PAUSED) {
            match.setMatchStatus(MatchStatus.MATCH_ACTIVE);
            matchRepository.save(match);
        }
        if (remaining != null && remaining <= 0) {
            state.setRoundExpiresAt(now);
            matchStateRepository.save(state);
            log.info("Match resumed with no remaining time after mime media recovered: match={}, user={}", matchId, userId);
            broadcastResumed(matchId, userId);
            matchEventPublisher.publishState(matchId);
            gameplayService.handleTimeout(matchId);
            return;
        }
        if (remaining != null) {
            state.setRoundExpiresAt(now.plusSeconds(remaining));
        }
        matchStateRepository.save(state);
        log.info("Match resumed after mime media recovered: match={}, user={}", matchId, userId);
        broadcastResumed(matchId, userId);
        matchEventPublisher.publishState(matchId);
    }

    private void broadcastPaused(UUID matchId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", matchId);
        data.put("pauseReason", PauseReason.MIME_MEDIA_FAILED.name());
        data.put("disconnectedUserId", null);
        data.put("reconnectDeadline", null);
        messagingTemplate.convertAndSend(
                "/topic/match/" + matchId + "/paused",
                new RealtimeEventEnvelopeDTO<>("MATCH_PAUSED", data, OffsetDateTime.now())
        );
    }

    private void broadcastResumed(UUID matchId, UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", matchId);
        data.put("userId", userId);
        messagingTemplate.convertAndSend(
                "/topic/match/" + matchId + "/resumed",
                new RealtimeEventEnvelopeDTO<>("MATCH_RESUMED", data, OffsetDateTime.now())
        );
    }

    private void requireMember(UUID matchId, UUID userId) {
        if (matchPlayerRepository.findByMatchIdAndUserId(matchId, userId).isEmpty()) {
            throw new IllegalArgumentException("actor is not in match");
        }
    }

    private void requireMime(MatchStateEntity state, UUID userId) {
        if (state.getCurrentMimePlayer() == null || !state.getCurrentMimePlayer().getId().equals(userId)) {
            throw new IllegalArgumentException("actor is not current mime player");
        }
    }

    private MatchStateEntity getState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
    }
}
