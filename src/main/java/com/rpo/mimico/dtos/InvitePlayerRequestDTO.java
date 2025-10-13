package com.rpo.mimico.dtos;

import java.util.UUID;

public record InvitePlayerRequestDTO(
        UUID tableId,
        String tableName,
        UUID invitedUserId
) {}
