package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.UUID;

@Builder
public record InviteResponseDTO(
        UUID tableId,
        String tableName,
        UUID hostId,
        UUID invitedUserId,
        Integer expiresIn
) {}