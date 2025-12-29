package com.rpo.mimico.services;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.dtos.TablePlayerDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String TABLE_INVITED_KEY_TEMPLATE = "table:%s:invited";
    private static final String TABLE_REJECTED_KEY_TEMPLATE = "table:%s:rejected";
    private static final int REQUIRED_PLAYERS = 4;

    private final StringRedisTemplate redisTemplate;
    private final GameTableRepository gameTableRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final SimpMessagingTemplate messagingTemplate;

    public void initializeTable(UUID hostUserId, String tableName) {
        UserEntity host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new IllegalArgumentException("Host not found: " + hostUserId));

        GameTableEntity table = GameTableEntity.builder()
                .name(tableName)
                .host(host)
                .status(GameTableEntity.TableStatus.WAITING)
                .build();

        GameTableEntity savedTable = gameTableRepository.save(table);

        final UUID tableId = savedTable.getId();
        log.info("Table {} created by user {}", tableId, hostUserId);

        String hostKey = String.format(TABLE_HOST_KEY_TEMPLATE, tableId);
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);

        redisTemplate.opsForValue().set(hostKey, hostUserId.toString());
        redisTemplate.opsForSet().add(acceptedKey, hostUserId.toString());

        log.info("Table initialized: tableId={}, host={}, acceptedCount=1/4", tableId, hostUserId);

        broadcastTablePlayersUpdate(tableId);
    }

    public void addInvitedPlayer(UUID tableId, UUID userId) {
        String invitedKey = String.format(TABLE_INVITED_KEY_TEMPLATE, tableId);
        redisTemplate.opsForSet().add(invitedKey, userId.toString());
        redisTemplate.expire(invitedKey, Duration.ofHours(1));

        log.info("Player added to invited list: tableId={}, userId={}", tableId, userId);

        broadcastTablePlayersUpdate(tableId);
    }

    public void addAcceptedPlayer(UUID tableId, UUID userId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        String invitedKey = String.format(TABLE_INVITED_KEY_TEMPLATE, tableId);

        Long addResult = redisTemplate.opsForSet().add(acceptedKey, userId.toString());

        redisTemplate.opsForSet().remove(invitedKey, userId.toString());

        if (addResult == null || addResult == 0) {
            log.warn("Player already accepted invite: tableId={}, userId={}", tableId, userId);
        }

        Long acceptedCount = redisTemplate.opsForSet().size(acceptedKey);

        log.info("Player accepted invite: tableId={}, userId={}, acceptedCount={}/4",
                tableId, userId, acceptedCount);

        broadcastTablePlayersUpdate(tableId);

        if (acceptedCount != null && acceptedCount >= REQUIRED_PLAYERS) {
            MatchResponseDTO matchResponse = startMatch(tableId);
            broadcastMatchStarted(tableId, matchResponse);
        }
    }

    public void addRejectedPlayer(UUID tableId, UUID userId) {
        String rejectedKey = String.format(TABLE_REJECTED_KEY_TEMPLATE, tableId);
        String invitedKey = String.format(TABLE_INVITED_KEY_TEMPLATE, tableId);

        redisTemplate.opsForSet().add(rejectedKey, userId.toString());
        redisTemplate.opsForSet().remove(invitedKey, userId.toString());
        redisTemplate.expire(rejectedKey, Duration.ofHours(1));

        log.info("Player rejected invite: tableId={}, userId={}", tableId, userId);

        broadcastTablePlayersUpdate(tableId);
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

        broadcastTablePlayersUpdate(tableId);
    }

    public List<String> getReadyPlayers(UUID tableId) {
        String readyKey = String.format(TABLE_READY_KEY_TEMPLATE, tableId);
        Set<String> readySet = redisTemplate.opsForSet().members(readyKey);

        return readySet != null ? List.copyOf(readySet) : List.of();
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

    private List<TablePlayerDTO> getTablePlayersWithDetails(UUID tableId) {
        String hostKey = String.format(TABLE_HOST_KEY_TEMPLATE, tableId);
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        String invitedKey = String.format(TABLE_INVITED_KEY_TEMPLATE, tableId);
        String rejectedKey = String.format(TABLE_REJECTED_KEY_TEMPLATE, tableId);
        String readyKey = String.format(TABLE_READY_KEY_TEMPLATE, tableId);

        String hostId = redisTemplate.opsForValue().get(hostKey);
        Set<String> acceptedIds = redisTemplate.opsForSet().members(acceptedKey);
        Set<String> invitedIds = redisTemplate.opsForSet().members(invitedKey);
        Set<String> rejectedIds = redisTemplate.opsForSet().members(rejectedKey);
        Set<String> readyIds = redisTemplate.opsForSet().members(readyKey);

        if (acceptedIds == null) acceptedIds = Set.of();
        if (invitedIds == null) invitedIds = Set.of();
        if (rejectedIds == null) rejectedIds = Set.of();
        if (readyIds == null) readyIds = Set.of();

        Set<UUID> allUserIds = new HashSet<>();
        acceptedIds.forEach(id -> allUserIds.add(UUID.fromString(id)));
        invitedIds.forEach(id -> allUserIds.add(UUID.fromString(id)));
        rejectedIds.forEach(id -> allUserIds.add(UUID.fromString(id)));

        if (allUserIds.isEmpty()) return List.of();

        Map<UUID, UserEntity> usersMap = userRepository.findAllById(allUserIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        List<TablePlayerDTO> players = new ArrayList<>();

        if (hostId != null) {
            UUID hostUuid = UUID.fromString(hostId);
            UserEntity hostUser = usersMap.get(hostUuid);
            if (hostUser != null) {
                String status = readyIds.contains(hostId) ? "ready" : "accepted";
                players.add(new TablePlayerDTO(hostUuid, hostUser.getNickname(), status));
            }
        }

        final Set<String> finalReadyIds = readyIds;
        final String finalHostId = hostId;
        acceptedIds.stream()
                .filter(id -> !id.equals(finalHostId))
                .forEach(id -> {
                    UUID uuid = UUID.fromString(id);
                    UserEntity user = usersMap.get(uuid);
                    if (user != null) {
                        String status = finalReadyIds.contains(id) ? "ready" : "accepted";
                        players.add(new TablePlayerDTO(uuid, user.getNickname(), status));
                    }
                });

        invitedIds.forEach(id -> {
            UUID uuid = UUID.fromString(id);
            UserEntity user = usersMap.get(uuid);
            if (user != null) {
                players.add(new TablePlayerDTO(uuid, user.getNickname(), "pending"));
            }
        });

        rejectedIds.forEach(id -> {
            UUID uuid = UUID.fromString(id);
            UserEntity user = usersMap.get(uuid);
            if (user != null) {
                players.add(new TablePlayerDTO(uuid, user.getNickname(), "rejected"));
            }
        });

        return players;
    }

    private void cleanupTable(UUID tableId) {
        String acceptedKey = String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
        String hostKey = String.format(TABLE_HOST_KEY_TEMPLATE, tableId);
        String readyKey = String.format(TABLE_READY_KEY_TEMPLATE, tableId);
        String invitedKey = String.format(TABLE_INVITED_KEY_TEMPLATE, tableId);
        String rejectedKey = String.format(TABLE_REJECTED_KEY_TEMPLATE, tableId);

        redisTemplate.delete(acceptedKey);
        redisTemplate.delete(hostKey);
        redisTemplate.delete(readyKey);
        redisTemplate.delete(invitedKey);
        redisTemplate.delete(rejectedKey);

        log.info("Table cleaned up: tableId={}", tableId);
    }

    private MatchResponseDTO startMatch(UUID tableId) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        if (table.getStatus() != GameTableEntity.TableStatus.WAITING) {
            throw new IllegalStateException("Table is not in WAITING status: " + table.getStatus());
        }

        Set<String> acceptedPlayerIds = getAcceptedPlayers(tableId);

        if (acceptedPlayerIds.size() != REQUIRED_PLAYERS) {
            String msg = String.format("Expected %d players, but found %d", REQUIRED_PLAYERS, acceptedPlayerIds.size());
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        List<UUID> playerIds = acceptedPlayerIds.stream().map(UUID::fromString).toList();

        log.info("Starting match: tableId={}, players={}", tableId, playerIds);

        StartMatchRequestDTO request = new StartMatchRequestDTO(tableId, playerIds);

        MatchResponseDTO matchResponse = matchService.startMatch(request);

        cleanupTable(tableId);

        log.info("Match started successfully: matchId={}, tableId={}", matchResponse.matchId(), tableId);

        return matchResponse;
    }

    private void broadcastTablePlayersUpdate(UUID tableId) {
        List<TablePlayerDTO> players = getTablePlayersWithDetails(tableId);

        log.info("Broadcasting table players update: tableId={}, players={}", tableId, players.size());

        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/players",
                Map.of(
                        "type", "TABLE_PLAYERS_UPDATE",
                        "players", players
                )
        );
    }

    private void broadcastMatchStarted(UUID tableId, MatchResponseDTO matchResponse) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/match-started",
                Map.of("type", "MATCH_STARTED", "data", matchResponse)
        );
    }
}