package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LeaveTableRequestDTO(
        @NotNull(message = "Table ID is required")
        UUID tableId
) {}
