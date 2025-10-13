package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StartMatchRequestDTO(
        @NotNull(message = "Table ID is required")
        UUID tableId,

        @NotNull(message = "Player IDs are required")
        @Size(min = 4, max = 4, message = "Exacly 4 players required")
        List<UUID> playerIds
){}

