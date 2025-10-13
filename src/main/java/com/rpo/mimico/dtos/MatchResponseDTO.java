package com.rpo.mimico.dtos;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MatchResponseDTO(
        UUID matchId,
        UUID tableId,
        Character startingTeam,
        UUID currentMimePlayerId,
        Integer teamAPosition,
        Integer teamBPosition,
        LocalDateTime startedAt
) {}