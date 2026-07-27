package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.UUID;

@Builder
public record InviteResponseDTO(
        UUID inviteId,
        UUID tableId,
        String tableName,
        UUID hostId,
        String hostDisplayName,
        UUID invitedUserId,
        Integer expiresIn
) {}
