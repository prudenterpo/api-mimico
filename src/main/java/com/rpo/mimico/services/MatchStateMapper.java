package com.rpo.mimico.services;

import com.rpo.mimico.domain.BoardRules;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.dtos.MatchStateResponseDTO;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;

import java.util.List;

public class MatchStateMapper {

    public MatchStateResponseDTO toDto(MatchEntity match, MatchStateEntity state, List<MatchPlayerEntity> players) {
        List<MatchStateResponseDTO.PlayerDTO> playerDtos = players.stream()
                .map(player -> MatchStateResponseDTO.PlayerDTO.builder()
                        .userId(player.getUser().getId())
                        .nickname(player.getNickname() != null ? player.getNickname() : player.getUser().getNickname())
                        .team(player.getTeam())
                        .playerOrder(player.getPlayerOrder())
                        .build())
                .toList();

        Integer currentPosition = currentPosition(state);
        boolean specialTile = isCurrentRoundSpecial(state, currentPosition);

        return MatchStateResponseDTO.builder()
                .matchId(match.getId())
                .tableId(match.getTable().getId())
                .players(playerDtos)
                .teamAPosition(state.getTeamAPosition())
                .teamBPosition(state.getTeamBPosition())
                .currentTurn(state.getCurrentTeam())
                .currentTeam(state.getCurrentTeam())
                .currentMimePlayerId(state.getCurrentMimePlayer() != null ? state.getCurrentMimePlayer().getId() : null)
                .gamePhase(gamePhase(state))
                .timerEndsAt(state.getRoundExpiresAt())
                .isSpecialTile(specialTile)
                .isPaused(state.getIsPaused())
                .matchStatus(match.getMatchStatus() != null ? match.getMatchStatus().name() : null)
                .roundState(state.getRoundState() != null ? state.getRoundState().name() : null)
                .pausedAt(state.getPausedAt())
                .pauseReason(state.getPauseReason() != null ? state.getPauseReason().name() : null)
                .disconnectedUserId(state.getDisconnectedUser() != null ? state.getDisconnectedUser().getId() : null)
                .reconnectDeadline(state.getReconnectDeadline())
                .winnerTeam(match.getWinnerTeam())
                .finishReason(match.getFinishReason() != null ? match.getFinishReason().name() : null)
                .build();
    }

    private String gamePhase(MatchStateEntity state) {
        if (Boolean.TRUE.equals(state.getIsPaused())) {
            return "paused";
        }
        if (state.getRoundState() == RoundState.ROUND_WAITING_FOR_WORD_SELECTION) {
            return "word-selection";
        }
        if (state.getRoundState() == RoundState.ROUND_GUESSING) {
            return "mime";
        }
        if (state.getRoundState() == RoundState.ROUND_WAITING_FOR_DICE || state.getRoundState() == RoundState.ROUND_RESOLVED) {
            return "dice";
        }
        if (state.getCurrentTeam() == null) {
            return "sorteio";
        }
        return "dice";
    }

    private boolean isCurrentRoundSpecial(MatchStateEntity state, Integer currentPosition) {
        if (currentPosition == null) {
            return false;
        }
        if (state.getRoundState() != RoundState.ROUND_WAITING_FOR_WORD_SELECTION
                && state.getRoundState() != RoundState.ROUND_GUESSING) {
            return BoardRules.isSpecial(currentPosition) && state.getRoundState() == null && state.getRoundExpiresAt() != null;
        }
        return BoardRules.isSpecial(currentPosition);
    }

    private Integer currentPosition(MatchStateEntity state) {
        if (state.getCurrentTeam() == null) {
            return state.getTeamAPosition();
        }
        return state.getCurrentTeam() == 'A' ? state.getTeamAPosition() : state.getTeamBPosition();
    }
}
