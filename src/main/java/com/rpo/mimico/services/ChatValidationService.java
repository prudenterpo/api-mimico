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
     * Processes a chat message from a player.
     * Business rules:
     * - If round is active (mime phase): validates as guess attempt
     * - If round is inactive (other phases): sends as free chat message
     */
    @Transactional
    public ChatValidationResultDTO processChatMessage(UUID matchId, ChatMessageDTO chatMessage) {
        MatchStateEntity matchState = getMatchState(matchId);

        boolean isRoundActive = isRoundActive(matchState);

        if (isRoundActive) {
            return validateGuess(matchId, chatMessage, matchState);
        } else {
            return sendFreeChatMessage(matchId, chatMessage);
        }
    }

    /*
     * Validates a guess during mime phase.
     * Business rules:
     * - Player must be allowed to chat based on current tile type
     * - Normal tiles: only mime's teammate can chat
     * - Special tiles: all 4 players can chat
     * - Mime player cannot chat (they're doing the mime!)
     * - Message is compared against current word (normalized, case-insensitive, ignore accents)
     */
    @Transactional
    public ChatValidationResultDTO validateGuess(UUID matchId, ChatMessageDTO chatMessage, MatchStateEntity matchState) {
        UUID userId = chatMessage.playerId();
        String chatMsg = chatMessage.message();
        UserEntity player = getUser(userId);

        validateChatPermission(matchState, userId);

        Character playerTeam = getPlayerTeam(matchId, userId);

        String normalizedGuess = WordNormalizer.normalize(chatMsg);
        String normalizedWord = WordNormalizer.normalize(matchState.getCurrentWord().getText());

        boolean isCorrect = normalizedGuess.equals(normalizedWord);

        log.info("Guess validated: match={}, player={}, team={}, message='{}', correct={}",
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

    private ChatValidationResultDTO sendFreeChatMessage(UUID matchId, ChatMessageDTO chatMessage) {
        UUID userId = chatMessage.playerId();
        UserEntity player = getUser(userId);
        Character playerTeam = getPlayerTeam(matchId, userId);

        log.info("Free chat message: match={}, player={}, team={}, message='{}'",
                matchId, userId, playerTeam, chatMessage.message());

        return ChatValidationResultDTO.builder()
                .playerId(chatMessage.playerId())
                .playerName(player.getNickname())
                .message(chatMessage.message())
                .isCorrect(false)
                .matchId(matchId)
                .guesserTeam(playerTeam)
                .build();
    }

    public boolean canPlayerChat(UUID matchId, UUID playerId) {
        try {
            MatchStateEntity matchState = getMatchState(matchId);

            if (!isRoundActive(matchState)) {
                return true;
            }

            validateChatPermission(matchState, playerId);
            return true;
        } catch (IllegalStateException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isRoundActive(MatchStateEntity matchState) {
        if (matchState.getRoundExpiresAt() == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(matchState.getRoundExpiresAt())) {
            return false;
        }

        if (matchState.getIsPaused()) {
            return false;
        }

        return true;
    }

    /*
     * Validates that the player is allowed to chat during mime phase.
     * Rules:
     * - Mime player cannot chat
     * - Normal tiles: only mime's teammate can chat
     * - Special tiles: all players except mime can chat
     */
    private void validateChatPermission(MatchStateEntity matchState, UUID playerId) {
        UUID mimePlayerId = matchState.getCurrentMimePlayer().getId();

        if (playerId.equals(mimePlayerId)) {
            throw new IllegalArgumentException("Mime player cannot send chat messages");
        }

        Character playerTeam = getPlayerTeam(matchState.getMatch().getId(), playerId);
        Character mimeTeam = matchState.getCurrentTeam();
        int currentPosition = getCurrentPosition(matchState);

        boolean isSpecialTile = SPECIAL_TILES.contains(currentPosition);

        if (!isSpecialTile && !playerTeam.equals(mimeTeam)) {
            throw new IllegalArgumentException("Only mime's teammate can chat on normal tiles");
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