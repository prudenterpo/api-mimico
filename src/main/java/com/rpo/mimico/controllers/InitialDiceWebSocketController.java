package com.rpo.mimico.controllers;

import com.rpo.mimico.services.InitialDiceService;
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

/*
 * WebSocket controller for the initial dice sorteio (UC08).
 *
 * Flow:
 * 1. Host sends SORTEIO_SELECT → /app/match/{matchId}/sorteio/select
 *    Payload: { playerAId: UUID, playerBId: UUID }
 *    Broadcast: SORTEIO_PLAYERS_SELECTED → /topic/match/{matchId}/sorteio
 *
 * 2. Each selected player sends → /app/match/{matchId}/sorteio/roll
 *    Broadcast per roll: SORTEIO_ROLL → /topic/match/{matchId}/sorteio
 *
 * 3. After both roll:
 *    - Tie:    SORTEIO_TIE    → /topic/match/{matchId}/sorteio (players re-roll)
 *    - Winner: SORTEIO_COMPLETE → /topic/match/{matchId}/sorteio (game moves to dice phase)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class InitialDiceWebSocketController {

    private final InitialDiceService initialDiceService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/match/{matchId}/sorteio/select")
    public void selectPlayers(
            @DestinationVariable UUID matchId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        UUID hostId = UUID.fromString(principal.getName());
        UUID playerAId = UUID.fromString(payload.get("playerAId"));
        UUID playerBId = UUID.fromString(payload.get("playerBId"));

        log.info("Sorteio player selection: match={}, host={}, playerA={}, playerB={}",
                matchId, hostId, playerAId, playerBId);

        try {
            initialDiceService.selectPlayers(matchId, hostId, playerAId, playerBId);
        } catch (Exception e) {
            log.warn("Sorteio select failed: match={}, error={}", matchId, e.getMessage());
            messagingTemplate.convertAndSendToUser(
                    hostId.toString(),
                    "/queue/errors",
                    Map.of("message", e.getMessage())
            );
        }
    }

    @MessageMapping("/match/{matchId}/sorteio/roll")
    public void roll(@DestinationVariable UUID matchId, Principal principal) {
        UUID playerId = UUID.fromString(principal.getName());

        log.info("Sorteio roll: match={}, player={}", matchId, playerId);

        try {
            initialDiceService.roll(matchId, playerId);
        } catch (Exception e) {
            log.warn("Sorteio roll failed: match={}, player={}, error={}", matchId, playerId, e.getMessage());
            messagingTemplate.convertAndSendToUser(
                    playerId.toString(),
                    "/queue/errors",
                    Map.of("message", e.getMessage())
            );
        }
    }
}
