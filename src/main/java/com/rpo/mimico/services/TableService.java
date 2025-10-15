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
                .status(GameTableEntity.TableStatus.WAITING)
                .build();

        GameTableEntity savedTable = gameTableRepository.save(table);

        tablePlayerService.initializeTable(savedTable.getId(), hostUserId);

        log.info("Table {} created by user {}", savedTable.getId(), hostUserId);

        return TableResponseDTO.builder()
                .id(savedTable.getId())
                .name(savedTable.getName())
                .hostId(savedTable.getHost().getId())
                .hostNickname(savedTable.getHost().getNickname())
                .status(savedTable.getStatus().name())
                .createdAt(savedTable.getCreatedAt())
                .build();
    }

    public TableResponseDTO getTable(UUID tableId) {
        GameTableEntity table = gameTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));

        return TableResponseDTO.builder()
                .id(table.getId())
                .name(table.getName())
                .hostId(table.getHost().getId())
                .hostNickname(table.getHost().getNickname())
                .status(table.getStatus().name())
                .createdAt(table.getCreatedAt())
                .build();
    }
}