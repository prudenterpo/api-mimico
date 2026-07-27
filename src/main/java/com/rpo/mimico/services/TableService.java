package com.rpo.mimico.services;

import com.rpo.mimico.dtos.CreateTableRequestDTO;
import com.rpo.mimico.dtos.TableResponseDTO;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableService {

    private final GameTableRepository gameTableRepository;
    private final UserRepository userRepository;
    private final TablePlayerService tablePlayerService;

    @Transactional
    public TableResponseDTO createTable(UUID hostUserId, CreateTableRequestDTO request) {
        UserEntity host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GameTableEntity table = GameTableEntity.builder()
                .name(request.name())
                .host(host)
                .status(GameTableEntity.TableStatus.TABLE_WAITING)
                .build();

        GameTableEntity savedTable = gameTableRepository.save(table);

        log.info("Table {} created by user {}", savedTable.getId(), hostUserId);

        tablePlayerService.initializeTableRedis(savedTable.getId(), hostUserId);

        return TableResponseDTO.builder()
                .tableId(savedTable.getId())
                .name(savedTable.getName())
                .hostUserId(savedTable.getHost().getId())
                .hostNickname(savedTable.getHost().getNickname())
                .status(savedTable.getStatus().name())
                .players(tablePlayerService.getTablePlayersWithDetails(savedTable.getId()))
                .teamAssignments(tablePlayerService.getTeamAssignments(savedTable.getId()))
                .createdAt(savedTable.getCreatedAt())
                .build();
    }

    public TableResponseDTO getTable(UUID tableId, UUID requesterUserId) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        if (!tablePlayerService.isAcceptedPlayer(tableId, requesterUserId)) {
            throw new IllegalArgumentException("User is not an accepted table player");
        }

        return TableResponseDTO.builder()
                .tableId(table.getId())
                .name(table.getName())
                .hostUserId(table.getHost().getId())
                .hostNickname(table.getHost().getNickname())
                .status(table.getStatus().name())
                .players(tablePlayerService.getTablePlayersWithDetails(tableId))
                .teamAssignments(tablePlayerService.getTeamAssignments(tableId))
                .createdAt(table.getCreatedAt())
                .build();
    }
}
