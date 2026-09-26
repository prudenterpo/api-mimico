package com.rpo.mimico.services;

import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.dtos.MatchEndedDTO;
import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.MatchStateResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.dtos.TeamAssignmentDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.exceptions.MatchNotFoundException;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import com.rpo.mimico.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchStateRepository matchStateRepository;
    private final GameTableRepository gameTableRepository;
    private final UserRepository userRepository;
    private final MatchStateMapper matchStateMapper = new MatchStateMapper();

    @Transactional
    public MatchResponseDTO startMatch(StartMatchRequestDTO request) {
        GameTableEntity table = gameTableRepository.findById(request.tableId())
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));

        if (table.getStatus() != GameTableEntity.TableStatus.TABLE_READY_TO_START) {
            throw new IllegalStateException("Table is not ready to start");
        }

        List<TeamAssignmentDTO> assignments = request.teamAssignments();
        validateTeamAssignments(assignments);

        MatchEntity match = MatchEntity.builder()
                .table(table)
                .matchStatus(MatchStatus.MATCH_SETUP)
                .startedAt(LocalDateTime.now())
                .build();
        match = matchRepository.save(match);

        List<PlayerTeam> playerTeams = assignments.stream()
                .flatMap(assignment -> assignment.playerIds().stream()
                        .map(playerId -> new PlayerTeam(playerId, assignment.team().charAt(0))))
                .sorted(Comparator.comparing(playerTeam -> playerTeam.playerId().toString()))
                .toList();

        for (int i = 0; i < playerTeams.size(); i++) {
            int finalI = i;
            PlayerTeam playerTeam = playerTeams.get(i);
            UserEntity user = userRepository.findById(playerTeam.playerId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + playerTeams.get(finalI).playerId()));

            MatchPlayerEntity matchPlayer = MatchPlayerEntity.builder()
                    .match(match)
                    .user(user)
                    .nickname(user.getNickname())
                    .team(playerTeam.team())
                    .playerOrder(i + 1)
                    .build();

            matchPlayerRepository.save(matchPlayer);
        }

        MatchStateEntity matchState = MatchStateEntity.builder()
                .match(match)
                .teamAPosition(0)
                .teamBPosition(0)
                .isPaused(false)
                .build();
        matchStateRepository.save(matchState);

        table.setStatus(GameTableEntity.TableStatus.TABLE_IN_MATCH);
        gameTableRepository.save(table);

        log.info("Match started (sorteio pending): id={}, tableId={}", match.getId(), table.getId());

        return MatchResponseDTO.builder()
                .matchId(match.getId())
                .tableId(table.getId())
                .status(match.getMatchStatus().name())
                .teamAPosition(0)
                .teamBPosition(0)
                .startedAt(match.getStartedAt())
                .build();
    }

    /*
     * Retrieves the current state of an active match for a given table.
     * Used for reconnection after page reload.
     * Returns null if no active match exists for the table.
     */
    @Transactional
    public MatchStateResponseDTO getActiveMatchByTableId(UUID tableId) {
        MatchEntity match = matchRepository.findByTableIdAndFinishedAtIsNull(tableId)
                .or(() -> matchRepository.findFirstByTable_IdOrderByStartedAtDesc(tableId))
                .orElseThrow(() -> new MatchNotFoundException(tableId));

        MatchStateEntity state = matchStateRepository.findByMatchId(match.getId())
                .orElseThrow(() -> new IllegalStateException("Match state not found for match: " + match.getId()));

        List<MatchPlayerEntity> matchPlayers = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(match.getId());
        return matchStateMapper.toDto(match, state, matchPlayers);
    }

    @Transactional
    public MatchEndedDTO abandonMatch(UUID tableId, UUID userId) {
        MatchEntity match = matchRepository.findByTableIdAndFinishedAtIsNull(tableId)
                .orElseThrow(() -> new IllegalArgumentException("No active match found for table: " + tableId));

        MatchPlayerEntity player = matchPlayerRepository.findByMatchIdAndUserId(match.getId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found in match: " + userId));

        Character abandonedTeam = player.getTeam();
        Character winnerTeam = abandonedTeam == 'A' ? 'B' : 'A';

        match.setWinnerTeam(winnerTeam);
        match.setFinishReason(FinishReason.MANUAL_FORFEIT);
        match.setMatchStatus(MatchStatus.MATCH_FINISHED);
        match.setFinishedAt(LocalDateTime.now());
        matchRepository.save(match);

        GameTableEntity table = match.getTable();
        table.setStatus(GameTableEntity.TableStatus.TABLE_BETWEEN_MATCHES);
        gameTableRepository.save(table);

        log.info("Match abandoned: matchId={}, tableId={}, abandonedBy={}, winnerTeam={}",
                match.getId(), tableId, userId, winnerTeam);

        return MatchEndedDTO.builder()
                .matchId(match.getId())
                .tableId(tableId)
                .winnerTeam(winnerTeam)
                .reason(FinishReason.MANUAL_FORFEIT.name())
                .abandonedByUserId(userId)
                .abandonedByNickname(player.getUser().getNickname())
                .build();
    }

    public MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found"));
    }

    private void validateTeamAssignments(List<TeamAssignmentDTO> assignments) {
        if (assignments == null || assignments.size() != 2) {
            throw new IllegalArgumentException("Team A and Team B assignments are required");
        }
        Set<String> teams = assignments.stream().map(TeamAssignmentDTO::team).collect(java.util.stream.Collectors.toSet());
        if (!teams.equals(Set.of("A", "B"))) {
            throw new IllegalArgumentException("Teams must be A and B");
        }
        List<UUID> playerIds = new ArrayList<>();
        assignments.forEach(assignment -> {
            if (assignment.playerIds() == null || assignment.playerIds().size() != 2) {
                throw new IllegalArgumentException("Each team must have exactly 2 players");
            }
            playerIds.addAll(assignment.playerIds());
        });
        if (playerIds.size() != 4 || new java.util.HashSet<>(playerIds).size() != 4) {
            throw new IllegalArgumentException("Exactly 4 unique players required");
        }
    }

    private record PlayerTeam(UUID playerId, Character team) {}
}
