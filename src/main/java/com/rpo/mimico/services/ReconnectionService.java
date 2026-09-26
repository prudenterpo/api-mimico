package com.rpo.mimico.services;

import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.PauseReason;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling player disconnections and reconnections during active matches.
 * Features:
 * - Pauses match immediately on disconnect
 * - 1-hour grace period for reconnection
 * - Host can manually end match (forfeit)
 * - Auto-forfeit after 1 hour if no reconnection
 * - Full game state restoration on reconnect
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconnectionService {

    private static final String RECONNECTION_KEY_TEMPLATE = "reconnection:%s:%s";
    public static final long RECONNECTION_TIMEOUT_SECONDS = 60;
    private static final long RECONNECTION_KEY_TTL_SECONDS = 90; // timeout + buffer for cleanup

    private final MatchStateRepository matchStateRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final GameTableRepository gameTableRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final TimerService timerService;
    private final GameplayService gameplayService;

    @Transactional
    public void handleDisconnect(UUID userId) {
        List<MatchPlayerEntity> activeMatches = matchPlayerRepository.findActiveMatchesByUserId(userId);

        if (activeMatches.isEmpty()) {
            log.debug("User {} disconnected but is not in any active match", userId);
            return;
        }

        MatchPlayerEntity matchPlayer = activeMatches.get(0);
        UUID matchId = matchPlayer.getMatch().getId();

        MatchStateEntity matchState = matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalStateException("Match state not found: " + matchId));

        if (Boolean.TRUE.equals(matchState.getIsPaused())) {
            log.warn("Match {} is already paused, not pausing again for user {}", matchId, userId);
            return;
        }

        MatchEntity match = matchState.getMatch();
        if (match.getFinishedAt() != null) {
            log.debug("Match {} is already finished, ignoring disconnect for user {}", matchId, userId);
            return;
        }

        LocalDateTime now = GameClock.toLocalDateTime(timerService.now());
        if (matchState.getRoundExpiresAt() != null) {
            long seconds = Duration.between(now, matchState.getRoundExpiresAt()).getSeconds();
            matchState.setRemainingRoundSecondsOnPause((int) Math.max(seconds, 0));
        }
        matchState.setIsPaused(true);
        matchState.setPausedAt(now);
        matchState.setPauseReason(PauseReason.PLAYER_DISCONNECTED);
        matchState.setDisconnectedUser(matchPlayer.getUser());
        matchState.setReconnectDeadline(now.plusSeconds(RECONNECTION_TIMEOUT_SECONDS));
        if (match.getMatchStatus() == MatchStatus.MATCH_ACTIVE) {
            match.setMatchStatus(MatchStatus.MATCH_PAUSED);
            matchRepository.save(match);
        }
        matchStateRepository.save(matchState);

        String reconnectionKey = buildReconnectionKey(matchId, userId);
        redisTemplate.opsForValue().set(
                reconnectionKey,
                LocalDateTime.now().toString(),
                RECONNECTION_KEY_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("Match paused due to disconnect: matchId={}, userId={}, gracePeriod={}s", matchId, userId, RECONNECTION_TIMEOUT_SECONDS);

        broadcastMatchPaused(matchId, userId, matchPlayer.getUser().getNickname());
    }

    @Transactional
    public void handleReconnect(UUID userId) {
        List<MatchPlayerEntity> activeMatches = matchPlayerRepository.findActiveMatchesByUserId(userId);

        if (activeMatches.isEmpty()) {
            log.debug("User {} reconnected but has no active matches", userId);
            return;
        }

        MatchPlayerEntity matchPlayer = activeMatches.get(0);
        UUID matchId = matchPlayer.getMatch().getId();

        String reconnectionKey = buildReconnectionKey(matchId, userId);
        String reconnectionData = redisTemplate.opsForValue().get(reconnectionKey);

        if (reconnectionData == null) {
            log.debug("No pending reconnection found for user {} in match {}", userId, matchId);
            return;
        }

        MatchStateEntity matchState = matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalStateException("Match state not found: " + matchId));

        Integer remaining = matchState.getRemainingRoundSecondsOnPause();
        matchState.setIsPaused(false);
        matchState.setPausedAt(null);
        matchState.setPauseReason(null);
        matchState.setDisconnectedUser(null);
        matchState.setReconnectDeadline(null);
        matchState.setRemainingRoundSecondsOnPause(null);
        if (matchState.getMatch().getMatchStatus() == MatchStatus.MATCH_PAUSED) {
            matchState.getMatch().setMatchStatus(MatchStatus.MATCH_ACTIVE);
            matchRepository.save(matchState.getMatch());
        }
        if (remaining != null) {
            LocalDateTime resumedAt = GameClock.toLocalDateTime(timerService.now());
            if (remaining <= 0) {
                matchState.setRoundExpiresAt(resumedAt);
                matchStateRepository.save(matchState);
                redisTemplate.delete(reconnectionKey);
                gameplayService.handleTimeout(matchId);
                log.info("Match resumed with no remaining time: matchId={}, userId={}", matchId, userId);
                broadcastMatchResumed(matchId, userId, matchPlayer.getUser().getNickname());
                return;
            }
            matchState.setRoundExpiresAt(resumedAt.plusSeconds(remaining));
        }
        matchStateRepository.save(matchState);

        redisTemplate.delete(reconnectionKey);

        log.info("Match resumed after reconnection: matchId={}, userId={}", matchId, userId);

        sendGameStateToPlayer(matchId, userId, matchState);

        broadcastMatchResumed(matchId, userId, matchPlayer.getUser().getNickname());
    }

    @Transactional
    public void forfeitMatch(UUID matchId, UUID disconnectedUserId) {
        MatchStateEntity matchState = matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));

        MatchEntity match = matchState.getMatch();
        if (match.getFinishedAt() != null) {
            log.debug("Match {} already finished, skipping forfeit", matchId);
            redisTemplate.delete(buildReconnectionKey(matchId, disconnectedUserId));
            return;
        }

        Character disconnectedTeam = getPlayerTeam(matchId, disconnectedUserId);
        Character winnerTeam = disconnectedTeam == 'A' ? 'B' : 'A';

        match.setWinnerTeam(winnerTeam);
        match.setFinishReason(FinishReason.RECONNECTION_FORFEIT);
        match.setMatchStatus(MatchStatus.MATCH_FINISHED);
        match.setFinishedAt(GameClock.toLocalDateTime(timerService.now()));
        matchRepository.save(match);

        GameTableEntity table = match.getTable();
        table.setStatus(GameTableEntity.TableStatus.TABLE_BETWEEN_MATCHES);
        gameTableRepository.save(table);

        redisTemplate.delete(buildReconnectionKey(matchId, disconnectedUserId));

        log.info("Match forfeited: matchId={}, disconnectedUser={}, winnerTeam={}",
                matchId, disconnectedUserId, winnerTeam);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", match.getId());
        data.put("tableId", table.getId());
        data.put("winnerTeam", winnerTeam.toString());
        data.put("finishReason", FinishReason.RECONNECTION_FORFEIT.name());
        messagingTemplate.convertAndSend(
                "/topic/table/" + table.getId() + "/closed",
                new RealtimeEventEnvelopeDTO<>("MATCH_ENDED", data, OffsetDateTime.now())
        );
    }

    public boolean hasPendingReconnection(UUID matchId) {
        List<MatchPlayerEntity> players = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId);

        for (MatchPlayerEntity player : players) {
            String reconnectionKey = buildReconnectionKey(matchId, player.getUser().getId());
            if (Boolean.TRUE.equals(redisTemplate.hasKey(reconnectionKey))) {
                return true;
            }
        }

        return false;
    }

    public UUID getDisconnectedPlayer(UUID matchId) {
        List<MatchPlayerEntity> players = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId);

        for (MatchPlayerEntity player : players) {
            String reconnectionKey = buildReconnectionKey(matchId, player.getUser().getId());
            if (Boolean.TRUE.equals(redisTemplate.hasKey(reconnectionKey))) {
                return player.getUser().getId();
            }
        }

        return null;
    }

    private Character getPlayerTeam(UUID matchId, UUID userId) {
        List<MatchPlayerEntity> players = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId);

        return players.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .map(MatchPlayerEntity::getTeam)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("PlayerEntity not in match: " + userId));
    }

    private String buildReconnectionKey(UUID matchId, UUID userId) {
        return String.format(RECONNECTION_KEY_TEMPLATE, matchId, userId);
    }

    private void broadcastMatchPaused(UUID matchId, UUID disconnectedUserId, String disconnectedUserNickname) {
        messagingTemplate.convertAndSend(
                "/topic/match/" + matchId + "/paused",
                Map.of(
                        "type", "MATCH_PAUSED",
                        "disconnectedUserId", disconnectedUserId.toString(),
                        "disconnectedUserNickname", disconnectedUserNickname,
                        "message", disconnectedUserNickname + " disconnected. Waiting " + RECONNECTION_TIMEOUT_SECONDS + "s for reconnection.",
                        "gracePeriodSeconds", RECONNECTION_TIMEOUT_SECONDS
                )
        );
    }

    private void broadcastMatchResumed(UUID matchId, UUID reconnectedUserId, String reconnectedUserNickname) {
        messagingTemplate.convertAndSend(
                "/topic/match/" + matchId + "/resumed",
                Map.of(
                        "type", "MATCH_RESUMED",
                        "reconnectedUserId", reconnectedUserId.toString(),
                        "reconnectedUserNickname", reconnectedUserNickname,
                        "message", reconnectedUserNickname + " reconnected. Match resumed."
                )
        );
    }

    private void sendGameStateToPlayer(UUID matchId, UUID userId, MatchStateEntity matchState) {
        Map<String, Object> gameState = Map.of(
                "type", "GAME_STATE_RESTORE",
                "matchId", matchId.toString(),
                "teamAPosition", matchState.getTeamAPosition(),
                "teamBPosition", matchState.getTeamBPosition(),
                "currentTeam", matchState.getCurrentTeam(),
                "currentMimePlayerId", matchState.getCurrentMimePlayer().getId().toString(),
                "isPaused", matchState.getIsPaused(),
                "roundExpiresAt", matchState.getRoundExpiresAt() != null
                        ? matchState.getRoundExpiresAt().toString()
                        : ""
        );

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/game-state",
                gameState
        );

        log.debug("GameEntity state sent to reconnected player: matchId={}, userId={}", matchId, userId);
    }
}
