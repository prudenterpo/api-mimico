package com.rpo.mimico.dtos;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MatchStateResponseDTO(
        UUID matchId,
        UUID tableId,
        List<PlayerDTO> players,
        Integer teamAPosition,
        Integer teamBPosition,
        Character currentTurn,
        UUID currentMimePlayerId,
        String gamePhase,
        LocalDateTime timerEndsAt,
        Boolean isSpecialTile,
        Boolean isPaused
) {
    @Builder
    public record PlayerDTO(
            UUID userId,
            String nickname,
            Character team,
            Integer playerOrder
    ) {}
}