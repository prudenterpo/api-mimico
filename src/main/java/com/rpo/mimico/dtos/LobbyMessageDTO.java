package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LobbyMessageDTO(
        @NotBlank(message = "Lobby message is required")
        @Size(max = 500, message = "Lobby message must be at most 500 characters")
        String message
) {}
