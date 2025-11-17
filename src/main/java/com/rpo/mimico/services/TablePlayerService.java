package com.rpo.mimico.services;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.repositories.GameTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/*
 * Service for managing players in a game table and triggering match start.
 * Tracks players who have accepted invites using Redis sets.
 * When all 4 players (including host) have accepted, automatically starts the match.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TablePlayerService {

    private static final String TABLE_ACCEPTED_KEY_TEMPLATE = "table:%s:accepted";
    private static final String TABLE_HOST_KEY_TEMPLATE = "table:%s:host";
    private static final String TABLE_READY_KEY_TEMPLATE = "table:%s:ready";
    private static final int REQUIRED_PLAYERS = 4;

    private final StringRedisTemplate redisTemplate;
    private final GameTableRepository gameTableRepository;
    private final MatchService matchService;
    private final SimpMessagingTemplate messagingTemplate;

    public void initializeTable(UUID tableId, UUID hostUserId) {
        String hostKey = String.format(TABLE_HOST_KEY_TEMPLATE, tableId);
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);

        redisTemplate.opsForValue().set(hostKey, hostUserId.toString());

        redisTemplate.opsForSet().add(acceptedKey, hostUserId.toString());

        log.info("Table initialized: tableId={}, host={}, acceptedCount=1/4", tableId, hostUserId);
    }

    public void addAcceptedPlayer(UUID tableId, UUID userId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);

        Long addResult = redisTemplate.opsForSet().add(acceptedKey, userId.toString());

        if (addResult == null || addResult == 0) {
            log.warn("Player already accepted invite: tableId={}, userId={}", tableId, userId);
        }

        Long acceptedCount = redisTemplate.opsForSet().size(acceptedKey);

        log.info("Player accepted invite: tableId={}, userId={}, acceptedCount={}/4",
                tableId, userId, acceptedCount);

        broadcastPlayerAccepted(tableId, userId);

        if (acceptedCount != null && acceptedCount >= REQUIRED_PLAYERS) {
            MatchResponseDTO matchResponse = startMatch(tableId);
            broadcastMatchStarted(tableId, matchResponse);
        }

        broadcastTableStatus(tableId, acceptedCount.intValue());
    }

    public void removeAcceptedPlayer(UUID tableId, UUID userId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        redisTemplate.opsForSet().remove(acceptedKey, userId.toString());

        log.info("Player removed from accepted list: tableId={}, userId={}", tableId, userId);
    }

    public Set<String> getAcceptedPlayers(UUID tableId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        return redisTemplate.opsForSet().members(acceptedKey);
    }

    public int getAcceptedCount(UUID tableId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        Long size = redisTemplate.opsForSet().size(acceptedKey);
        return size != null ? size.intValue() : 0;
    }

    public boolean isTableReady(UUID tableId) {
        return getAcceptedCount(tableId) >= REQUIRED_PLAYERS;
    }

    public void cleanupTable(UUID tableId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        String hostKey = String.format(TABLE_HOST_KEY_TEMPLATE, tableId);
        String readyKey = String.format(TABLE_READY_KEY_TEMPLATE, tableId);

        redisTemplate.delete(acceptedKey);
        redisTemplate.delete(hostKey);
        redisTemplate.delete(readyKey);

        log.info("Table cleaned up: tableId={}", tableId);
    }

    public void setPlayerReady(UUID tableId, UUID userId, boolean isReady) {
        String readyKey = String.format(TABLE_READY_KEY_TEMPLATE, tableId);

        if (isReady) {
            redisTemplate.opsForSet().add(readyKey, userId.toString());
            log.info("Player marked as ready: tableId={}, userId{}", tableId, userId);

        } else {
            redisTemplate.opsForSet().remove(readyKey, userId.toString());
            log.info("Player marked as not ready: tableId={}, userId{}", tableId, userId);
        }

        redisTemplate.expire(readyKey, Duration.ofHours(1));
    }

    public List<String> getReadyPlayers(UUID tableId) {
        String readyKey = String.format(TABLE_READY_KEY_TEMPLATE, tableId);
        Set<String> readySet = redisTemplate.opsForSet().members(readyKey);

        return readySet != null ? List.copyOf(readySet) : List.of();
    }

    private MatchResponseDTO startMatch(UUID tableId) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        if (table.getStatus() != GameTableEntity.TableStatus.WAITING) {
            throw new IllegalStateException("Table is not in WAITING status: " + table.getStatus());
        }

        Set<String> acceptedPlayerIds = getAcceptedPlayers(tableId);

        if (acceptedPlayerIds.size() != REQUIRED_PLAYERS) {
            throw new IllegalStateException(
                    String.format("Expected %d players, but found %d", REQUIRED_PLAYERS, acceptedPlayerIds.size())
            );
        }

        List<UUID> playerIds = acceptedPlayerIds.stream().map(UUID::fromString).toList();

        log.info("Starting match: tableId={}, players={}", tableId, playerIds);

        StartMatchRequestDTO request = new StartMatchRequestDTO(tableId, playerIds);

        MatchResponseDTO matchResponse = matchService.startMatch(request);

        cleanupTable(tableId);

        log.info("Match started successfully: matchId={}, tableId={}", matchResponse.matchId(), tableId);

        return matchResponse;
    }

    private void broadcastPlayerAccepted(UUID tableId, UUID userId) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/player-accepted",
                Map.of("type", "PLAYER_ACCEPTED", "userId", userId.toString())
        );
    }

    private void broadcastTableStatus(UUID tableId, int acceptedCount) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/status",
                Map.of("type", "TABLE_STATUS", "acceptedCount", acceptedCount, "requiredCount", 4)
        );
    }

    private void broadcastMatchStarted(UUID tableId, MatchResponseDTO matchResponse) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/match-started",
                Map.of("type", "MATCH_STARTED", "data", matchResponse)
        );
    }
}