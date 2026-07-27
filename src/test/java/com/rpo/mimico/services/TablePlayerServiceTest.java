package com.rpo.mimico.services;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.TeamAssignmentDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TablePlayerServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private GameTableRepository gameTableRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private InviteService inviteService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final Map<String, Set<String>> sets = new HashMap<>();
    private final Map<String, String> values = new HashMap<>();

    private TablePlayerService service;
    private UUID tableId;
    private UserEntity host;
    private UserEntity player2;
    private UserEntity player3;
    private UserEntity player4;
    private UserEntity outsider;
    private GameTableEntity table;

    @BeforeEach
    void setUp() {
        tableId = UUID.randomUUID();
        host = user(UUID.randomUUID(), "host_1");
        player2 = user(UUID.randomUUID(), "player_2");
        player3 = user(UUID.randomUUID(), "player_3");
        player4 = user(UUID.randomUUID(), "player_4");
        outsider = user(UUID.randomUUID(), "outsider");
        table = GameTableEntity.builder()
                .id(tableId)
                .name("Mesa V1")
                .host(host)
                .status(GameTableEntity.TableStatus.TABLE_WAITING)
                .createdAt(LocalDateTime.now())
                .build();

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.members(anyString())).thenAnswer(invocation -> sets.getOrDefault(invocation.getArgument(0), Set.of()));
        when(setOperations.add(anyString(), anyString())).thenAnswer(invocation -> {
            sets.computeIfAbsent(invocation.getArgument(0), ignored -> new LinkedHashSet<>()).add(invocation.getArgument(1));
            return 1L;
        });
        when(setOperations.remove(anyString(), anyString())).thenAnswer(invocation -> {
            sets.computeIfAbsent(invocation.getArgument(0), ignored -> new LinkedHashSet<>()).remove(invocation.getArgument(1));
            return 1L;
        });
        when(valueOperations.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            sets.remove(invocation.getArgument(0));
            values.remove(invocation.getArgument(0));
            return true;
        });
        when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation -> {
            Collection<?> keys = invocation.getArgument(0);
            keys.forEach(key -> {
                sets.remove(key.toString());
                values.remove(key.toString());
            });
            return (long) keys.size();
        });

        when(gameTableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(gameTableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            Map<UUID, UserEntity> users = Map.of(
                    host.getId(), host,
                    player2.getId(), player2,
                    player3.getId(), player3,
                    player4.getId(), player4,
                    outsider.getId(), outsider
            );
            java.util.ArrayList<UserEntity> result = new java.util.ArrayList<>();
            ids.forEach(id -> {
                if (users.containsKey(id)) {
                    result.add(users.get(id));
                }
            });
            return result;
        });
        when(userRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            Map<UUID, UserEntity> users = Map.of(
                    host.getId(), host,
                    player2.getId(), player2,
                    player3.getId(), player3,
                    player4.getId(), player4,
                    outsider.getId(), outsider
            );
            return Optional.ofNullable(users.get(id));
        });

        service = new TablePlayerService(
                redisTemplate,
                gameTableRepository,
                userRepository,
                matchService,
                inviteService,
                messagingTemplate
        );
        service.initializeTableRedis(tableId, host.getId());
    }

    @Test
    void sendInviteRejectsSelfDuplicateAndTooManyInvites() {
        assertThrows(IllegalArgumentException.class, () -> service.sendInvite(tableId, host.getId(), host.getId()));

        when(userRepository.findById(player2.getId())).thenReturn(Optional.of(player2));
        when(inviteService.createInvite(tableId, player2.getId(), host.getId())).thenReturn(UUID.randomUUID());
        when(inviteService.inviteTimeoutSeconds()).thenReturn(60L);

        service.sendInvite(tableId, host.getId(), player2.getId());
        assertThrows(IllegalArgumentException.class, () -> service.sendInvite(tableId, host.getId(), player2.getId()));

        sets.put(invitedKey(), new LinkedHashSet<>(Set.of(
                player2.getId().toString(), player3.getId().toString(), player4.getId().toString()
        )));
        assertThrows(IllegalStateException.class, () -> service.sendInvite(tableId, host.getId(), outsider.getId()));
    }

    @Test
    void acceptExpiredInviteFailsAndBroadcastsPlayersUpdatedEnvelope() {
        UUID inviteId = UUID.randomUUID();
        when(inviteService.inviteExists(tableId, inviteId, player2.getId())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.acceptInvite(tableId, inviteId, player2.getId()));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/table/" + tableId + "/players"), captor.capture());
        assertEquals("TABLE_PLAYERS_UPDATED", assertEnvelope(captor.getValue()).type());
        assertTrue(sets.get(expiredKey()).contains(player2.getId().toString()));
    }

    @Test
    void validTeamAssignmentMakesTableReadyAndPublishesTeamsUpdated() {
        acceptFourPlayers();

        service.assignTeams(tableId, host.getId(), validAssignments());

        assertEquals(GameTableEntity.TableStatus.TABLE_READY_TO_START, table.getStatus());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/table/" + tableId + "/teams"), captor.capture());
        assertEquals("TABLE_TEAMS_UPDATED", assertEnvelope(captor.getValue()).type());
    }

    @Test
    void invalidTeamAssignmentsAreRejected() {
        acceptFourPlayers();

        assertThrows(IllegalArgumentException.class, () -> service.assignTeams(tableId, host.getId(), List.of(
                new TeamAssignmentDTO("A", List.of(host.getId(), player2.getId())),
                new TeamAssignmentDTO("C", List.of(player3.getId(), player4.getId()))
        )));
        assertThrows(IllegalArgumentException.class, () -> service.assignTeams(tableId, host.getId(), List.of(
                new TeamAssignmentDTO("A", List.of(host.getId(), player2.getId())),
                new TeamAssignmentDTO("B", List.of(player2.getId(), player4.getId()))
        )));
        assertThrows(IllegalArgumentException.class, () -> service.assignTeams(tableId, host.getId(), List.of(
                new TeamAssignmentDTO("A", List.of(host.getId(), player2.getId())),
                new TeamAssignmentDTO("B", List.of(player3.getId(), outsider.getId()))
        )));
        assertThrows(IllegalArgumentException.class, () -> service.assignTeams(tableId, host.getId(), List.of(
                new TeamAssignmentDTO("A", List.of(host.getId(), player2.getId())),
                new TeamAssignmentDTO("B", List.of(player3.getId()))
        )));
    }

    @Test
    void nonHostCannotAssignTeamsOrStartMatch() {
        acceptFourPlayers();

        assertThrows(IllegalArgumentException.class, () -> service.assignTeams(tableId, player2.getId(), validAssignments()));
        assertThrows(IllegalArgumentException.class, () -> service.startMatch(tableId, player2.getId(), validAssignments()));
    }

    @Test
    void hostStartRequiresReadinessAndPublishesMatchStarted() {
        assertThrows(IllegalStateException.class, () -> service.startMatch(tableId, host.getId(), validAssignments()));

        acceptFourPlayers();
        service.assignTeams(tableId, host.getId(), validAssignments());
        MatchResponseDTO response = MatchResponseDTO.builder()
                .matchId(UUID.randomUUID())
                .tableId(tableId)
                .status("MATCH_SETUP")
                .teamAPosition(0)
                .teamBPosition(0)
                .startedAt(LocalDateTime.now())
                .build();
        when(matchService.startMatch(any())).thenReturn(response);

        service.startMatch(tableId, host.getId(), validAssignments());

        verify(messagingTemplate).convertAndSend(eq("/topic/table/" + tableId + "/match-started"), any(Object.class));
    }

    @Test
    void tableChatRequiresAcceptedPlayerAndCanonicalEnvelope() {
        service.postTableMessage(tableId, host.getId(), "  oi mesa  ");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/table/" + tableId + "/chat"), captor.capture());
        assertEquals("TABLE_MESSAGE_POSTED", assertEnvelope(captor.getValue()).type());

        assertThrows(IllegalArgumentException.class, () -> service.postTableMessage(tableId, outsider.getId(), "oi"));
        assertThrows(IllegalArgumentException.class, () -> service.postTableMessage(tableId, host.getId(), " "));
        assertThrows(IllegalArgumentException.class, () -> service.postTableMessage(tableId, host.getId(), "x".repeat(501)));
    }

    @Test
    void leaveRemovesNonHostAndHostLeaveClosesTable() {
        acceptFourPlayers();
        service.assignTeams(tableId, host.getId(), validAssignments());

        service.leaveTable(tableId, player2.getId());

        assertEquals(GameTableEntity.TableStatus.TABLE_WAITING, table.getStatus());
        assertTrue(!sets.get(acceptedKey()).contains(player2.getId().toString()));

        service.leaveTable(tableId, host.getId());

        assertEquals(GameTableEntity.TableStatus.TABLE_CLOSED, table.getStatus());
        verify(messagingTemplate).convertAndSend(eq("/topic/table/" + tableId + "/closed"), any(Object.class));
    }

    private void acceptFourPlayers() {
        sets.computeIfAbsent(acceptedKey(), ignored -> new LinkedHashSet<>()).addAll(List.of(
                host.getId().toString(),
                player2.getId().toString(),
                player3.getId().toString(),
                player4.getId().toString()
        ));
    }

    private List<TeamAssignmentDTO> validAssignments() {
        return List.of(
                new TeamAssignmentDTO("A", List.of(host.getId(), player2.getId())),
                new TeamAssignmentDTO("B", List.of(player3.getId(), player4.getId()))
        );
    }

    private RealtimeEventEnvelopeDTO<?> assertEnvelope(Object value) {
        RealtimeEventEnvelopeDTO<?> envelope = assertInstanceOf(RealtimeEventEnvelopeDTO.class, value);
        assertTrue(envelope.occurredAt() != null);
        assertTrue(envelope.data() != null);
        return envelope;
    }

    private String acceptedKey() {
        return "table:%s:accepted".formatted(tableId);
    }

    private String invitedKey() {
        return "table:%s:invited".formatted(tableId);
    }

    private String rejectedKey() {
        return "table:%s:rejected".formatted(tableId);
    }

    private String expiredKey() {
        return "table:%s:expired".formatted(tableId);
    }

    private UserEntity user(UUID id, String nickname) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }
}
