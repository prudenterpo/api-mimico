package com.rpo.mimico.controllers;

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
            gameplayService.rollDice(matchId, userId(principal));
            log.info("Dice rolled: match={}", matchId);
        } catch (Exception e) {
            log.error("Error rolling dice: {}", e.getMessage());
            sendErrorToUser(userId(principal), e.getMessage());
        }
    }

    @MessageMapping("/match/{matchId}/word/draw")
    public void drawWordCard(@DestinationVariable UUID matchId, Principal principal) {
        try {
            gameplayService.drawWordCard(matchId, userId(principal));
            log.info("Word card sent privately to mime player: match={}", matchId);
        } catch (Exception e) {
            log.error("Error drawing word card: {}", e.getMessage());
            sendErrorToUser(userId(principal), e.getMessage());
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

            gameplayService.selectWord(matchId, wordId, userId(principal));

            log.info("Word selected and round started: match={}", matchId);
        } catch (Exception e) {
            log.error("Error selecting word: {}", e.getMessage());
            sendErrorToUser(UUID.fromString(principal.getName()), e.getMessage());
        }
    }

    private UUID userId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        return UUID.fromString(principal.getName());
    }

    private void sendErrorToUser(UUID userId, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/error",
                Map.of("message", message)
        );
    }
}