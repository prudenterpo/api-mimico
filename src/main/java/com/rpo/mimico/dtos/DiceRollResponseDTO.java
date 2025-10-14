package com.rpo.mimico.dtos;

import lombok.Builder;

@Builder
public record DiceRollResponseDTO(
        Integer value,
        Character team
) {}