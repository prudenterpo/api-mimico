package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TableMessageDTO(
        @NotBlank(message = "Table message is required")
        @Size(max = 500, message = "Table message must be at most 500 characters")
        String message
) {}
