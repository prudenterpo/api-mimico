package com.rpo.mimico.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTableRequestDTO(
        @NotBlank(message = "Table name is required")
        @Size(min = 3, max = 100, message = "Table name must be between 3 and 100 characters")
        String name
) {
}
