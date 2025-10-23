package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LobbyMessageDTO(
        @NotBlank String userId,
        @NotBlank String userName,
        @NotBlank @Size(max = 500) String message,
        String timestamp
) {}