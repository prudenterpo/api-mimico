package com.rpo.mimico.dtos;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record TableResponseDTO(
        UUID tableId,
        String name,
        UUID hostUserId,
        String hostNickname,
        String status,
        List<TablePlayerDTO> players,
        List<TeamAssignmentDTO> teamAssignments,
        LocalDateTime createdAt
) {}
