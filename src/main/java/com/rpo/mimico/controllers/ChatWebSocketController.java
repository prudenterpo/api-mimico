package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.ChatMessageDTO;
import com.rpo.mimico.dtos.ChatValidationResultDTO;
import com.rpo.mimico.dtos.ErrorResponseDTO;
import com.rpo.mimico.services.ChatValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/*
 * WebSocket controller for handling chat messages during gameplay.
 *
 * Players send guesses via:
 *   /app/match/{matchId}/chat
 *
 * All players receive validation results via:
 *   /topic/match/{matchId}/chat
 *
 * Message flow:
 * 1. Player sends guess → /app/match/{matchId}/chat
 * 2. Server validates guess (ChatValidationService)
 * 3. Server broadcasts result → /topic/match/{matchId}/chat
 * 4. If correct: GameplayService handles turn logic
 * 5. All players see the message + validation result
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatValidationService chatValidationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/match/{matchId}/chat")
    public void handleChatMessage(@DestinationVariable UUID matchId, @Payload ChatMessageDTO chatMessage) {

        log.info("Chat message received: match={}, player={}, message='{}'",
                matchId, chatMessage.playerId(), chatMessage.message());

        try {
            ChatValidationResultDTO result = chatValidationService.validateGuess(matchId, chatMessage);

            messagingTemplate.convertAndSend("/topic/match/" + matchId + "/chat", result);

            if (result.isCorrect()) {
                log.info("Correct guess! match={}, player={}, team={}", matchId, result.playerId(), result.guesserTeam());

                messagingTemplate.convertAndSend("/topic/match/" + matchId + "/correct-guess", result);
            }

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("Chat message rejected: match={}, player={}, reason={}", matchId, chatMessage.playerId(), e.getMessage());

            messagingTemplate.convertAndSendToUser(
                    chatMessage.playerId().toString(),
                    "/queue/errors",
                    new ErrorResponseDTO(e.getMessage())
            );
        }
    }
}