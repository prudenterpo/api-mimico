package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.MatchEndedDTO;
import com.rpo.mimico.services.MatchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class MatchWebSocketController {

    private final MatchService matchService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void init() {
        log.info("MatchWebSocketController initialized!");
    }

    @MessageMapping("/match/abandon")
    public void abandonMatch(@Payload Map<String, String> payload, Principal principal) {
        log.info("=== abandonMatch method called ===");

        UUID userId = UUID.fromString(principal.getName());
        UUID tableId = UUID.fromString(payload.get("tableId"));

        log.info("Player abandoning match: tableId={}, userId={}", tableId, userId);

        try {
            MatchEndedDTO result = matchService.abandonMatch(tableId, userId);

            messagingTemplate.convertAndSend(
                    "/topic/table/" + tableId + "/match-ended",
                    Map.of(
                            "type", "MATCH_ENDED",
                            "data", result
                    )
            );

            log.info("Match ended broadcast sent: tableId={}, winnerTeam={}", tableId, result.winnerTeam());

        } catch (Exception e) {
            log.error("Error abandoning match: tableId={}, userId={}, error={}", tableId, userId, e.getMessage(), e);

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/errors",
                    Map.of("message", "Failed to abandon match: " + e.getMessage())
            );
        }
    }
}