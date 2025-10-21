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
        @Size(min = 5, max = 20, message = "Nickname must be between 5 and 20 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message ="Nickname must be a single word containing only letters, numbers, hyphens, or underscores"
        )
        String nickname
) {}
