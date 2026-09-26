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
        Boolean isPaused,
        String matchStatus,
        String roundState,
        Character currentTeam,
        LocalDateTime pausedAt,
        String pauseReason,
        UUID disconnectedUserId,
        LocalDateTime reconnectDeadline,
        Character winnerTeam,
        String finishReason
) {
    @Builder
    public record PlayerDTO(
            UUID userId,
            String nickname,
            Character team,
            Integer playerOrder
    ) {}
}