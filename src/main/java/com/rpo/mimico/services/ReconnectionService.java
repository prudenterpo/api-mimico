package com.rpo.mimico.services;

import com.rpo.mimico.dtos.MatchEndedDTO;
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

import java.time.LocalDateTime;
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

        matchState.setIsPaused(true);
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

        matchState.setIsPaused(false);
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
        match.setFinishedAt(LocalDateTime.now());
        matchRepository.save(match);

        GameTableEntity table = match.getTable();
        table.setStatus(GameTableEntity.TableStatus.TABLE_BETWEEN_MATCHES);
        gameTableRepository.save(table);

        redisTemplate.delete(buildReconnectionKey(matchId, disconnectedUserId));

        log.info("Match forfeited: matchId={}, disconnectedUser={}, winnerTeam={}",
                matchId, disconnectedUserId, winnerTeam);

        MatchEndedDTO matchEndedDTO = MatchEndedDTO.builder()
                .matchId(match.getId())
                .tableId(table.getId())
                .winnerTeam(winnerTeam)
                .reason("FORFEIT")
                .build();

        messagingTemplate.convertAndSend(
                "/topic/table/" + table.getId() + "/match-ended",
                Map.of("type", "MATCH_ENDED", "data", matchEndedDTO)
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
