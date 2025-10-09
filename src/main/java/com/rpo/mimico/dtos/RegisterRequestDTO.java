package com.rpo.mimico.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "E-mail is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$",
                message = "Password must contain at least one uppercase letter, one lowercase " +
                        "letter, one number and one special character"
        )
        String password,

        @NotBlank(message = "Nickname is required")
        @Size(min = 3, max = 50, message = "Nickname must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message = "Nickname can only contain letters, numbers, hyphens and underscores"
        )
        String nickname
) {}
