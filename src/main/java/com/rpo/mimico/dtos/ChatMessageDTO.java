package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatMessageDTO(
        @NotNull(message = "Player ID is required")
        UUID playerId,

        @NotBlank(message = "Message cannot be empty")
        @Size(max = 500, message = "Message too long (max 500 characters)")
        String message
) {}