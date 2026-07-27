package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TeamAssignmentDTO(
        @NotBlank(message = "Team is required")
        String team,

        @NotNull(message = "Player IDs are required")
        @Size(min = 2, max = 2, message = "Each team must have exactly 2 players")
        List<UUID> playerIds
) {}
