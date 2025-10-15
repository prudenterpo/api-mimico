package com.rpo.mimico.services;

import com.rpo.mimico.dtos.ChatMessageDTO;
import com.rpo.mimico.dtos.ChatValidationResultDTO;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import com.rpo.mimico.repositories.UserRepository;
import com.rpo.mimico.utils.WordNormalizer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatValidationService {

    private static final Set<Integer> SPECIAL_TILES = Set.of(5, 11, 17, 23, 29, 35, 40, 44, 48, 51);

    private final MatchStateRepository matchStateRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final UserRepository userRepository;
    private final GameplayService gameplayService;

    /*
     * Validates a chat message (guess) from a player.
     * Business rules:
     * - Player must be allowed to chat based on current tile type
     * - Normal tiles: only mime's teammate can chat
     * - Special tiles: all 4 players can chat
     * - Mime player cannot chat (they're doing the mime!)
     * - Round must be active (timer not expired)
     * - Message is compared against current word (normalized)
     */
    @Transactional
    public ChatValidationResultDTO validateGuess(UUID matchId, ChatMessageDTO chatMessage) {
        MatchStateEntity matchState = getMatchState(matchId);
        UUID userId = chatMessage.playerId();
        String chatMsg = chatMessage.message();
        UserEntity player = getUser(userId);

        validateRoundActive(matchState);

        validateChatPermission(matchState, userId);

        Character playerTeam = getPlayerTeam(matchId, userId);

        String normalizedGuess = WordNormalizer.normalize(chatMsg);
        String normalizedWord = WordNormalizer.normalize(matchState.getCurrentWord().getText());

        boolean isCorrect = normalizedGuess.equals(normalizedWord);

        log.info("Chat message validated: match={}, player={}, team={}, message='{}', correct={}",
                matchId, chatMessage.playerId(), playerTeam, chatMessage.message(), isCorrect);

        if (isCorrect) {
            gameplayService.handleCorrectGuess(matchId, userId);
        }

        return ChatValidationResultDTO.builder()
                .playerId(chatMessage.playerId())
                .playerName(player.getNickname())
                .message(chatMessage.message())
                .isCorrect(isCorrect)
                .matchId(matchId)
                .guesserTeam(playerTeam)
                .build();
    }

    /**
     * Checks if a player is allowed to send chat messages based on:
     * - Current tile type (normal vs special)
     * - Player's team vs mime's team
     * - Player is not the mime
     */
    public boolean canPlayerChat(UUID matchId, UUID playerId) {
        try {
            MatchStateEntity matchState = getMatchState(matchId);
            validateRoundActive(matchState);
            validateChatPermission(matchState, playerId);
            return true;
        } catch (IllegalStateException | IllegalArgumentException e) {
            return false;
        }
    }

    private void validateRoundActive(MatchStateEntity matchState) {
        if (matchState.getRoundExpiresAt() == null) {
            throw new IllegalStateException("No active round - cannot send chat messages");
        }

        if (LocalDateTime.now().isAfter(matchState.getRoundExpiresAt())) {
            throw new IllegalStateException("Round has expired - cannot send chat messages");
        }

        if (matchState.getIsPaused()) {
            throw new IllegalStateException("Match is paused - cannot send chat messages");
        }
    }

    /*
     * Validates that the player is allowed to chat based on game rules.
     * Rules:
     * - Mime player cannot chat
     * - Normal tiles: only mime's teammate can chat
     * - Special tiles: all players except mime can chat
     */
    private void validateChatPermission(MatchStateEntity matchState, UUID playerId) {
        UUID mimePlayerId = matchState.getCurrentMimePlayer().getId();

        // Mime cannot chat (they're doing the mime!)
        if (playerId.equals(mimePlayerId)) {
            throw new IllegalArgumentException("Mime player cannot send chat messages");
        }

        Character playerTeam = getPlayerTeam(matchState.getMatch().getId(), playerId);
        Character mimeTeam = matchState.getCurrentTeam();
        int currentPosition = getCurrentPosition(matchState);

        boolean isSpecialTile = SPECIAL_TILES.contains(currentPosition);

        if (isSpecialTile) {
            log.debug("Special tile - all players can chat: position={}", currentPosition);
        } else {
            if (!playerTeam.equals(mimeTeam)) {
                throw new IllegalArgumentException("Only mime's teammate can chat on normal tiles");
            }
            log.debug("Normal tile - only teammate can chat: position={}, mimeTeam={}", currentPosition, mimeTeam);
        }
    }

    private int getCurrentPosition(MatchStateEntity matchState) {
        return matchState.getCurrentTeam() == 'A'
                ? matchState.getTeamAPosition()
                : matchState.getTeamBPosition();
    }

    private Character getPlayerTeam(UUID matchId, UUID playerId) {
        List<MatchPlayerEntity> allPlayers = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId);

        return allPlayers.stream()
                .filter(p -> p.getUser().getId().equals(playerId))
                .map(MatchPlayerEntity::getTeam)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not in match: " + playerId));
    }

    private MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}















































