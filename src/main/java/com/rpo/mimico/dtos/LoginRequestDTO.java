package com.rpo.mimico.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "E-mail is required")
        @Email(message = "E-mail must be valid")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
