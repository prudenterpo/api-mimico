package com.rpo.mimico.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AssignTeamsRequestDTO(
        @NotNull(message = "Table ID is required")
        UUID tableId,

        @Valid
        @NotNull(message = "Team assignments are required")
        @Size(min = 2, max = 2, message = "Team A and Team B assignments are required")
        List<TeamAssignmentDTO> teamAssignments
) {}
