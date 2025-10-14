package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.DiceRollResponseDTO;
import com.rpo.mimico.dtos.WordCardResponseDTO;
import com.rpo.mimico.services.GameplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameplayWebSocketController {

    private final GameplayService gameplayService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/match/{matchId}/dice/roll")
    public void rollDice(@DestinationVariable UUID matchId, Principal principal) {
        try {
            DiceRollResponseDTO result = gameplayService.rollDice(matchId);

            messagingTemplate.convertAndSend(
                    "/topic/match/" + matchId + "/dice",
                    Map.of(
                            "type", "DICE_ROLLED",
                            "data", result
                    )
            );

            log.info("Dice rolled broadcasted: match={}, value={}", matchId, result.value());
        } catch (Exception e) {
            log.error("Error rolling dice: {}", e.getMessage());
            sendErrorToUser(UUID.fromString(principal.getName()), e.getMessage());
        }
    }

    @MessageMapping("/match/{matchId}/word/draw")
    public void drawWordCard(@DestinationVariable UUID matchId, Principal principal) {
        try {
            WordCardResponseDTO wordCard = gameplayService.drawWordCard(matchId);

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/word-card",
                    Map.of(
                            "type", "WORD_CARD",
                            "data", wordCard
                    )
            );

            log.info("Word card sent to mime player: match={}", matchId);
        } catch (Exception e) {
            log.error("Error drawing word card: {}", e.getMessage());
            sendErrorToUser(UUID.fromString(principal.getName()), e.getMessage());
        }
    }

    @MessageMapping("/match/{matchId}/word/select")
    public void selectWord(
            @DestinationVariable UUID matchId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        try {
            UUID wordId = UUID.fromString(payload.get("wordId"));

            gameplayService.selectWord(matchId, wordId);

            messagingTemplate.convertAndSend(
                    "/topic/match/" + matchId + "/round",
                    Map.of(
                            "type", "ROUND_STARTED",
                            "mimePlayerId", principal.getName(),
                            "expiresIn", 60
                    )
            );

            log.info("Word selected and round started: match={}", matchId);
        } catch (Exception e) {
            log.error("Error selecting word: {}", e.getMessage());
            sendErrorToUser(UUID.fromString(principal.getName()), e.getMessage());
        }
    }

    private void sendErrorToUser(UUID userId, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/error",
                Map.of("message", message)
        );
    }
}