package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteDecisionRequestDTO(
        @NotNull(message = "Table ID is required")
        UUID tableId,

        @NotNull(message = "Invite ID is required")
        UUID inviteId
) {}
