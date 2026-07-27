package com.rpo.mimico.services;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.dtos.TeamAssignmentDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import com.rpo.mimico.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private GameTableRepository gameTableRepository;
    @Mock
    private UserRepository userRepository;

    private MatchService service;
    private UUID tableId;
    private GameTableEntity table;
    private UserEntity p1;
    private UserEntity p2;
    private UserEntity p3;
    private UserEntity p4;

    @BeforeEach
    void setUp() {
        service = new MatchService(matchRepository, matchPlayerRepository, matchStateRepository, gameTableRepository, userRepository);
        tableId = UUID.randomUUID();
        UserEntity host = user(UUID.randomUUID(), "host_1");
        table = GameTableEntity.builder()
                .id(tableId)
                .name("Mesa V1")
                .host(host)
                .status(GameTableEntity.TableStatus.TABLE_READY_TO_START)
                .build();
        p1 = host;
        p2 = user(UUID.randomUUID(), "player_2");
        p3 = user(UUID.randomUUID(), "player_3");
        p4 = user(UUID.randomUUID(), "player_4");

        when(gameTableRepository.findById(tableId)).thenReturn(Optional.of(table));
        when(matchRepository.save(any())).thenAnswer(invocation -> {
            MatchEntity match = invocation.getArgument(0);
            if (match.getId() == null) {
                match.setId(UUID.randomUUID());
            }
            if (match.getStartedAt() == null) {
                match.setStartedAt(LocalDateTime.now());
            }
            return match;
        });
        when(matchStateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameTableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(p1.getId())).thenReturn(Optional.of(p1));
        when(userRepository.findById(p2.getId())).thenReturn(Optional.of(p2));
        when(userRepository.findById(p3.getId())).thenReturn(Optional.of(p3));
        when(userRepository.findById(p4.getId())).thenReturn(Optional.of(p4));
    }

    @Test
    void startMatchCreatesSetupMatchAndPreservesExplicitTeamSnapshot() {
        MatchResponseDTO response = service.startMatch(new StartMatchRequestDTO(tableId, assignments()));

        assertEquals("MATCH_SETUP", response.status());
        assertEquals(GameTableEntity.TableStatus.TABLE_IN_MATCH, table.getStatus());

        ArgumentCaptor<MatchPlayerEntity> playerCaptor = ArgumentCaptor.forClass(MatchPlayerEntity.class);
        verify(matchPlayerRepository, times(4)).save(playerCaptor.capture());

        List<MatchPlayerEntity> players = playerCaptor.getAllValues();
        assertEquals(2, players.stream().filter(player -> player.getTeam() == 'A').count());
        assertEquals(2, players.stream().filter(player -> player.getTeam() == 'B').count());
        assertEquals('A', teamFor(players, p1.getId()));
        assertEquals('A', teamFor(players, p2.getId()));
        assertEquals('B', teamFor(players, p3.getId()));
        assertEquals('B', teamFor(players, p4.getId()));

        ArgumentCaptor<MatchStateEntity> stateCaptor = ArgumentCaptor.forClass(MatchStateEntity.class);
        verify(matchStateRepository).save(stateCaptor.capture());
        assertEquals(0, stateCaptor.getValue().getTeamAPosition());
        assertEquals(0, stateCaptor.getValue().getTeamBPosition());
    }

    @Test
    void startMatchRejectsLegacyOrInvalidTeamAssignments() {
        assertThrows(IllegalArgumentException.class, () -> service.startMatch(new StartMatchRequestDTO(tableId, null)));
        assertThrows(IllegalArgumentException.class, () -> service.startMatch(new StartMatchRequestDTO(tableId, List.of(
                new TeamAssignmentDTO("A", List.of(p1.getId(), p2.getId())),
                new TeamAssignmentDTO("B", List.of(p2.getId(), p4.getId()))
        ))));

        table.setStatus(GameTableEntity.TableStatus.TABLE_WAITING);
        assertThrows(IllegalStateException.class, () -> service.startMatch(new StartMatchRequestDTO(tableId, assignments())));
    }

    private Character teamFor(List<MatchPlayerEntity> players, UUID userId) {
        return players.stream()
                .filter(player -> player.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow()
                .getTeam();
    }

    private List<TeamAssignmentDTO> assignments() {
        return List.of(
                new TeamAssignmentDTO("A", List.of(p1.getId(), p2.getId())),
                new TeamAssignmentDTO("B", List.of(p3.getId(), p4.getId()))
        );
    }

    private UserEntity user(UUID id, String nickname) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }
}
