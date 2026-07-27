package com.rpo.mimico.services;

import com.rpo.mimico.dtos.InviteResponseDTO;
import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.dtos.TableMessageDataDTO;
import com.rpo.mimico.dtos.TablePlayerDTO;
import com.rpo.mimico.dtos.TeamAssignmentDTO;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TablePlayerService {

    private static final String TABLE_ACCEPTED_KEY_TEMPLATE = "table:%s:accepted";
    private static final String TABLE_HOST_KEY_TEMPLATE = "table:%s:host";
    private static final String TABLE_INVITED_KEY_TEMPLATE = "table:%s:invited";
    private static final String TABLE_REJECTED_KEY_TEMPLATE = "table:%s:rejected";
    private static final String TABLE_EXPIRED_KEY_TEMPLATE = "table:%s:expired";
    private static final String TABLE_TEAM_KEY_TEMPLATE = "table:%s:team:%s";
    private static final int REQUIRED_PLAYERS = 4;
    private static final int MAX_INVITED_USERS = 3;
    private static final int TABLE_TTL_HOURS = 2;

    private final StringRedisTemplate redisTemplate;
    private final GameTableRepository gameTableRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final InviteService inviteService;
    private final SimpMessagingTemplate messagingTemplate;

    public void initializeTableRedis(UUID tableId, UUID hostUserId) {
        redisTemplate.opsForValue().set(hostKey(tableId), hostUserId.toString(), Duration.ofHours(TABLE_TTL_HOURS));
        redisTemplate.opsForSet().add(acceptedKey(tableId), hostUserId.toString());
        redisTemplate.expire(acceptedKey(tableId), Duration.ofHours(TABLE_TTL_HOURS));
        log.info("Table initialized: tableId={}, host={}", tableId, hostUserId);
    }

    public InviteResponseDTO sendInvite(UUID tableId, UUID hostUserId, UUID invitedUserId) {
        GameTableEntity table = requireOpenTable(tableId);
        requireHost(table, hostUserId);

        if (hostUserId.equals(invitedUserId)) {
            throw new IllegalArgumentException("Host cannot invite self");
        }
        if (isAcceptedPlayer(tableId, invitedUserId)) {
            throw new IllegalArgumentException("User is already in table");
        }
        if (inviteService.inviteExists(tableId, invitedUserId) || setMembers(invitedKey(tableId)).contains(invitedUserId.toString())) {
            throw new IllegalArgumentException("User is already invited");
        }
        if (setMembers(invitedKey(tableId)).size() >= MAX_INVITED_USERS) {
            throw new IllegalStateException("Table can only invite 3 users for V1");
        }

        UserEntity invitedUser = userRepository.findById(invitedUserId)
                .orElseThrow(() -> new IllegalArgumentException("Invited user not found"));
        UUID inviteId = inviteService.createInvite(tableId, invitedUserId, hostUserId);
        redisTemplate.opsForSet().add(invitedKey(tableId), invitedUserId.toString());
        redisTemplate.expire(invitedKey(tableId), Duration.ofHours(TABLE_TTL_HOURS));

        InviteResponseDTO invite = InviteResponseDTO.builder()
                .inviteId(inviteId)
                .tableId(tableId)
                .tableName(table.getName())
                .hostId(hostUserId)
                .hostDisplayName(table.getHost().getNickname())
                .invitedUserId(invitedUser.getId())
                .expiresIn((int) inviteService.inviteTimeoutSeconds())
                .build();

        messagingTemplate.convertAndSendToUser(
                invitedUserId.toString(),
                "/queue/invite",
                envelope("TABLE_INVITE_RECEIVED", invite)
        );
        broadcastTablePlayersUpdate(tableId);
        return invite;
    }

    public void acceptInvite(UUID tableId, UUID inviteId, UUID userId) {
        requireOpenTable(tableId);
        if (!inviteService.inviteExists(tableId, inviteId, userId)) {
            redisTemplate.opsForSet().remove(invitedKey(tableId), userId.toString());
            redisTemplate.opsForSet().add(expiredKey(tableId), userId.toString());
            redisTemplate.expire(expiredKey(tableId), Duration.ofHours(TABLE_TTL_HOURS));
            broadcastTablePlayersUpdate(tableId);
            throw new IllegalStateException("Invite not found or expired");
        }
        if (getAcceptedCount(tableId) >= REQUIRED_PLAYERS) {
            throw new IllegalStateException("Table is full");
        }

        inviteService.removeInvite(inviteId);
        redisTemplate.opsForSet().remove(invitedKey(tableId), userId.toString());
        redisTemplate.opsForSet().add(acceptedKey(tableId), userId.toString());
        redisTemplate.expire(acceptedKey(tableId), Duration.ofHours(TABLE_TTL_HOURS));
        updateTableReadiness(tableId);
        broadcastTablePlayersUpdate(tableId);
        log.info("Invite accepted: tableId={}, userId={}", tableId, userId);
    }

    public void rejectInvite(UUID tableId, UUID inviteId, UUID userId) {
        requireOpenTable(tableId);
        inviteService.removeInvite(inviteId);
        inviteService.removeInvite(tableId, userId);
        redisTemplate.opsForSet().remove(invitedKey(tableId), userId.toString());
        redisTemplate.opsForSet().add(rejectedKey(tableId), userId.toString());
        redisTemplate.expire(rejectedKey(tableId), Duration.ofHours(TABLE_TTL_HOURS));
        broadcastTablePlayersUpdate(tableId);
        log.info("Invite rejected: tableId={}, userId={}", tableId, userId);
    }

    public void assignTeams(UUID tableId, UUID hostUserId, List<TeamAssignmentDTO> assignments) {
        GameTableEntity table = requireOpenTable(tableId);
        requireHost(table, hostUserId);
        validateTeamAssignments(tableId, assignments);

        redisTemplate.delete(List.of(teamKey(tableId, "A"), teamKey(tableId, "B")));
        assignments.forEach(assignment -> {
            String key = teamKey(tableId, assignment.team());
            assignment.playerIds().forEach(playerId -> redisTemplate.opsForSet().add(key, playerId.toString()));
            redisTemplate.expire(key, Duration.ofHours(TABLE_TTL_HOURS));
        });
        updateTableReadiness(tableId);
        broadcastTableTeamsUpdate(tableId);
        log.info("Teams assigned: tableId={}, host={}", tableId, hostUserId);
    }

    public MatchResponseDTO startMatch(UUID tableId, UUID hostUserId, List<TeamAssignmentDTO> requestedAssignments) {
        GameTableEntity table = requireOpenTable(tableId);
        requireHost(table, hostUserId);
        List<TeamAssignmentDTO> assignments = requestedAssignments == null || requestedAssignments.isEmpty()
                ? getTeamAssignments(tableId)
                : requestedAssignments;
        validateTeamAssignments(tableId, assignments);
        updateTableReadiness(tableId);

        GameTableEntity readyTable = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        if (readyTable.getStatus() != GameTableEntity.TableStatus.TABLE_READY_TO_START) {
            throw new IllegalStateException("Table is not ready to start");
        }

        MatchResponseDTO match = matchService.startMatch(new StartMatchRequestDTO(tableId, assignments));
        cleanupPendingInvites(tableId);
        broadcastMatchStarted(tableId, match);
        return match;
    }

    public void leaveTable(UUID tableId, UUID userId) {
        GameTableEntity table = requireOpenTable(tableId);
        if (table.getHost().getId().equals(userId)) {
            closeTable(tableId, "HOST_LEFT");
            return;
        }
        if (!isAcceptedPlayer(tableId, userId)) {
            throw new IllegalArgumentException("User is not an accepted table player");
        }
        redisTemplate.opsForSet().remove(acceptedKey(tableId), userId.toString());
        redisTemplate.delete(List.of(teamKey(tableId, "A"), teamKey(tableId, "B")));
        updateTableReadiness(tableId);
        broadcastTablePlayersUpdate(tableId);
        broadcastTableTeamsUpdate(tableId);
        log.info("Player left table: tableId={}, userId={}", tableId, userId);
    }

    public TableMessageDataDTO postTableMessage(UUID tableId, UUID userId, String rawMessage) {
        requireOpenTable(tableId);
        if (!isAcceptedPlayer(tableId, userId)) {
            throw new IllegalArgumentException("Only accepted table players can send table chat");
        }
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Table message is required");
        }
        String message = rawMessage.trim();
        if (message.length() > 500) {
            throw new IllegalArgumentException("Table message must be at most 500 characters");
        }
        UserEntity sender = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        TableMessageDataDTO data = new TableMessageDataDTO(tableId, userId, sender.getNickname(), message, OffsetDateTime.now());
        messagingTemplate.convertAndSend("/topic/table/" + tableId + "/chat", envelope("TABLE_MESSAGE_POSTED", data));
        return data;
    }

    public List<TablePlayerDTO> getTablePlayersWithDetails(UUID tableId) {
        String hostId = redisTemplate.opsForValue().get(hostKey(tableId));
        Set<String> acceptedIds = setMembers(acceptedKey(tableId));
        Set<String> invitedIds = setMembers(invitedKey(tableId));
        Set<String> rejectedIds = setMembers(rejectedKey(tableId));
        Set<String> expiredIds = setMembers(expiredKey(tableId));

        Set<UUID> allUserIds = new LinkedHashSet<>();
        acceptedIds.forEach(id -> allUserIds.add(UUID.fromString(id)));
        invitedIds.forEach(id -> allUserIds.add(UUID.fromString(id)));
        rejectedIds.forEach(id -> allUserIds.add(UUID.fromString(id)));
        expiredIds.forEach(id -> allUserIds.add(UUID.fromString(id)));

        Map<UUID, UserEntity> usersMap = userRepository.findAllById(allUserIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        List<TablePlayerDTO> players = new ArrayList<>();
        addPlayers(players, usersMap, hostId == null ? Set.of() : Set.of(hostId), "accepted");
        addPlayers(players, usersMap, without(acceptedIds, hostId), "accepted");
        addPlayers(players, usersMap, invitedIds, "pending");
        addPlayers(players, usersMap, rejectedIds, "rejected");
        addPlayers(players, usersMap, expiredIds, "expired");
        return players;
    }

    public List<TeamAssignmentDTO> getTeamAssignments(UUID tableId) {
        return List.of(
                new TeamAssignmentDTO("A", sortedUuidList(teamKey(tableId, "A"))),
                new TeamAssignmentDTO("B", sortedUuidList(teamKey(tableId, "B")))
        );
    }

    public int getAcceptedCount(UUID tableId) {
        return setMembers(acceptedKey(tableId)).size();
    }

    public boolean isAcceptedPlayer(UUID tableId, UUID userId) {
        return setMembers(acceptedKey(tableId)).contains(userId.toString());
    }

    public void closeTable(UUID tableId, String reason) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        table.setStatus(GameTableEntity.TableStatus.TABLE_CLOSED);
        gameTableRepository.save(table);
        cleanupTable(tableId);
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/closed",
                envelope("TABLE_CLOSED", Map.of("tableId", tableId, "reason", reason))
        );
        log.info("Table closed: tableId={}, reason={}", tableId, reason);
    }

    private void validateTeamAssignments(UUID tableId, List<TeamAssignmentDTO> assignments) {
        if (assignments == null || assignments.size() != 2) {
            throw new IllegalArgumentException("Team A and Team B assignments are required");
        }
        Set<String> teams = assignments.stream().map(TeamAssignmentDTO::team).collect(Collectors.toSet());
        if (!teams.equals(Set.of("A", "B"))) {
            throw new IllegalArgumentException("Teams must be A and B");
        }

        Set<UUID> accepted = setMembers(acceptedKey(tableId)).stream().map(UUID::fromString).collect(Collectors.toSet());
        if (accepted.size() != REQUIRED_PLAYERS) {
            throw new IllegalStateException("Exactly 4 accepted players are required");
        }

        Set<UUID> seen = new HashSet<>();
        for (TeamAssignmentDTO assignment : assignments) {
            if (assignment.playerIds() == null || assignment.playerIds().size() != 2) {
                throw new IllegalArgumentException("Each team must have exactly 2 players");
            }
            for (UUID playerId : assignment.playerIds()) {
                if (!accepted.contains(playerId)) {
                    throw new IllegalArgumentException("Player is not accepted in table: " + playerId);
                }
                if (!seen.add(playerId)) {
                    throw new IllegalArgumentException("Player cannot be assigned to multiple teams: " + playerId);
                }
            }
        }
        if (!seen.equals(accepted)) {
            throw new IllegalArgumentException("Every accepted player must be assigned exactly once");
        }
    }

    private void updateTableReadiness(UUID tableId) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        if (table.getStatus() == GameTableEntity.TableStatus.TABLE_CLOSED
                || table.getStatus() == GameTableEntity.TableStatus.TABLE_IN_MATCH) {
            return;
        }
        boolean ready = getAcceptedCount(tableId) == REQUIRED_PLAYERS && hasValidStoredTeams(tableId);
        table.setStatus(ready ? GameTableEntity.TableStatus.TABLE_READY_TO_START : GameTableEntity.TableStatus.TABLE_WAITING);
        gameTableRepository.save(table);
    }

    private boolean hasValidStoredTeams(UUID tableId) {
        try {
            validateTeamAssignments(tableId, getTeamAssignments(tableId));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private GameTableEntity requireOpenTable(UUID tableId) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        if (table.getStatus() == GameTableEntity.TableStatus.TABLE_CLOSED) {
            throw new IllegalStateException("Table is closed");
        }
        if (table.getStatus() == GameTableEntity.TableStatus.TABLE_IN_MATCH) {
            throw new IllegalStateException("Table is already in match");
        }
        return table;
    }

    private void requireHost(GameTableEntity table, UUID userId) {
        if (!table.getHost().getId().equals(userId)) {
            throw new IllegalArgumentException("Only the table host can perform this action");
        }
    }

    private void broadcastTablePlayersUpdate(UUID tableId) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/players",
                envelope("TABLE_PLAYERS_UPDATED", Map.of(
                        "tableId", tableId,
                        "players", getTablePlayersWithDetails(tableId)
                ))
        );
    }

    private void broadcastTableTeamsUpdate(UUID tableId) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/teams",
                envelope("TABLE_TEAMS_UPDATED", Map.of(
                        "tableId", tableId,
                        "teamAssignments", getTeamAssignments(tableId)
                ))
        );
    }

    private void broadcastMatchStarted(UUID tableId, MatchResponseDTO matchResponse) {
        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/match-started",
                envelope("MATCH_STARTED", matchResponse)
        );
    }

    private void cleanupPendingInvites(UUID tableId) {
        redisTemplate.delete(invitedKey(tableId));
        redisTemplate.delete(rejectedKey(tableId));
        redisTemplate.delete(expiredKey(tableId));
    }

    private void cleanupTable(UUID tableId) {
        redisTemplate.delete(List.of(
                acceptedKey(tableId),
                hostKey(tableId),
                invitedKey(tableId),
                rejectedKey(tableId),
                expiredKey(tableId),
                teamKey(tableId, "A"),
                teamKey(tableId, "B")
        ));
    }

    private RealtimeEventEnvelopeDTO<Object> envelope(String type, Object data) {
        return new RealtimeEventEnvelopeDTO<>(type, data, OffsetDateTime.now());
    }

    private Set<String> setMembers(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    private List<UUID> sortedUuidList(String key) {
        return setMembers(key).stream().map(UUID::fromString).sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private Set<String> without(Set<String> values, String excluded) {
        if (excluded == null) {
            return values;
        }
        return values.stream().filter(value -> !value.equals(excluded)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void addPlayers(List<TablePlayerDTO> players, Map<UUID, UserEntity> users, Set<String> ids, String status) {
        ids.forEach(id -> {
            UserEntity user = users.get(UUID.fromString(id));
            if (user != null) {
                players.add(new TablePlayerDTO(user.getId(), user.getNickname(), status));
            }
        });
    }

    private String acceptedKey(UUID tableId) {
        return String.format(TABLE_ACCEPTED_KEY_TEMPLATE, tableId);
    }

    private String hostKey(UUID tableId) {
        return String.format(TABLE_HOST_KEY_TEMPLATE, tableId);
    }

    private String invitedKey(UUID tableId) {
        return String.format(TABLE_INVITED_KEY_TEMPLATE, tableId);
    }

    private String rejectedKey(UUID tableId) {
        return String.format(TABLE_REJECTED_KEY_TEMPLATE, tableId);
    }

    private String expiredKey(UUID tableId) {
        return String.format(TABLE_EXPIRED_KEY_TEMPLATE, tableId);
    }

    private String teamKey(UUID tableId, String team) {
        return String.format(TABLE_TEAM_KEY_TEMPLATE, tableId, team);
    }
}
