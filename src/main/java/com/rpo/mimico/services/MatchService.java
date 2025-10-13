package com.rpo.mimico.services;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
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

    private final Random random = new Random();

    @Transactional
    public MatchResponseDTO startMatch(StartMatchRequestDTO request) {
        GameTableEntity table = gameTableRepository.findById(request.tableId())
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));

        if (table.getStatus() != GameTableEntity.TableStatus.WAITING) {
            throw new IllegalStateException("Table is not in WAITING status");
        }

        if (request.playerIds().size() != 4) {
            throw new IllegalArgumentException("Exactly 4 players required");
        }

        MatchEntity match = MatchEntity.builder()
                .table(table)
                .startedAt(LocalDateTime.now())
                .build();
        match = matchRepository.save(match);

        List<UUID> playerIds = request.playerIds();
        for (int i = 0; i < playerIds.size(); i++) {
            int finalI = i;
            UserEntity user = userRepository.findById(playerIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + playerIds.get(finalI)));

            Character team = (i % 2 == 0) ? 'A' : 'B';

            MatchPlayerEntity matchPlayer = MatchPlayerEntity.builder()
                    .match(match)
                    .user(user)
                    .team(team)
                    .playerOrder(i)
                    .build();

            matchPlayerRepository.save(matchPlayer);
        }

        Character startingTeam = random.nextBoolean() ? 'A' : 'B';

        List<MatchPlayerEntity> startingTeamPlayers = matchPlayerRepository
                .findByMatchIdAndTeam(match.getId(), startingTeam);
        UserEntity firstMimePlayer = startingTeamPlayers.get(0).getUser();

        MatchStateEntity matchState = MatchStateEntity.builder()
                .match(match)
                .teamAPosition(0)
                .teamBPosition(0)
                .currentTeam(startingTeam)
                .currentMimePlayer(firstMimePlayer)
                .isPaused(false)
                .build();
        matchStateRepository.save(matchState);

        table.setStatus(GameTableEntity.TableStatus.IN_PROGRESS);
        gameTableRepository.save(table);

        log.info("Match started: id={}, tableId={}, startingTeam={}", match.getId(), table.getId(), startingTeam);

        return MatchResponseDTO.builder()
                .matchId(match.getId())
                .tableId(table.getId())
                .startingTeam(startingTeam)
                .currentMimePlayerId(firstMimePlayer.getId())
                .teamAPosition(0)
                .teamBPosition(0)
                .startedAt(match.getStartedAt())
                .build();
    }

    public MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found"));
    }
}
