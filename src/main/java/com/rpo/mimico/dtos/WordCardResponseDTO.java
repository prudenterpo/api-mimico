package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record WordCardResponseDTO(
        UUID matchId,
        List<WordOption> words
) {
    public record WordOption(
            UUID wordId,
            String text,
            String category
    ) {}
}