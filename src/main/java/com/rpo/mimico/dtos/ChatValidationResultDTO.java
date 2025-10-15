package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ChatValidationResultDTO(
        UUID playerId,
        String playerName,
        String message,
        boolean isCorrect,
        UUID matchId,
        Character guesserTeam
) {}