package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.UUID;

@Builder
public record MatchEndedDTO(
        UUID matchId,
        UUID tableId,
        Character winnerTeam,
        String reason,
        UUID abandonedByUserId,
        String abandonedByNickname
) {}