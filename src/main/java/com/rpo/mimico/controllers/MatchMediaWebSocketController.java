package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.ErrorResponseDTO;
import com.rpo.mimico.dtos.MediaSignalCommandDTO;
import com.rpo.mimico.services.MatchMediaService;
import com.rpo.mimico.services.MatchSignalingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchMediaWebSocketController {

    private final MatchSignalingService matchSignalingService;
    private final MatchMediaService matchMediaService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/match/{matchId}/signal")
    public void signal(
            @DestinationVariable UUID matchId,
            @Payload MediaSignalCommandDTO command,
            Principal principal
    ) {
        UUID userId = null;
        try {
            userId = userId(principal);
            matchSignalingService.signal(matchId, userId, command);
        } catch (RuntimeException e) {
            log.warn("Signal rejected: match={}, user={}, reason={}", matchId, userId, e.getMessage());
            if (userId != null) {
                sendError(userId, e.getMessage());
            }
        }
    }

    @MessageMapping("/match/{matchId}/media/unavailable")
    public void mediaUnavailable(@DestinationVariable UUID matchId, Principal principal) {
        UUID userId = null;
        try {
            userId = userId(principal);
            matchMediaService.reportUnavailable(matchId, userId);
        } catch (RuntimeException e) {
            log.warn("Media unavailable rejected: match={}, user={}, reason={}", matchId, userId, e.getMessage());
            if (userId != null) {
                sendError(userId, e.getMessage());
            }
        }
    }

    @MessageMapping("/match/{matchId}/media/available")
    public void mediaAvailable(@DestinationVariable UUID matchId, Principal principal) {
        UUID userId = null;
        try {
            userId = userId(principal);
            matchMediaService.reportAvailable(matchId, userId);
        } catch (RuntimeException e) {
            log.warn("Media available rejected: match={}, user={}, reason={}", matchId, userId, e.getMessage());
            if (userId != null) {
                sendError(userId, e.getMessage());
            }
        }
    }

    private UUID userId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        return UUID.fromString(principal.getName());
    }

    private void sendError(UUID userId, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/errors",
                new ErrorResponseDTO(message)
        );
    }
}
