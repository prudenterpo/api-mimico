package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.InvitePlayerRequestDTO;
import com.rpo.mimico.dtos.InviteResponseDTO;
import com.rpo.mimico.services.InviteService;
import com.rpo.mimico.services.OnlineUsersService;
import com.rpo.mimico.services.TablePlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TableWebSocketController {

    private final InviteService inviteService;
    private final OnlineUsersService onlineUsersService;
    private final TablePlayerService tablePlayerService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/table/invite")
    public void sendInvite(@Payload InvitePlayerRequestDTO request, Principal principal) {
        UUID hostUserId = UUID.fromString(principal.getName());
        UUID invitedUserId = request.invitedUserId();
        UUID tableId = request.tableId();

        if (!onlineUsersService.isUserOnline(invitedUserId)) {
            sendErrorToUser(hostUserId, "User is not online");
            return;
        }

        inviteService.createInvite(tableId, invitedUserId, hostUserId);

        InviteResponseDTO inviteData = InviteResponseDTO.builder()
                .tableId(tableId)
                .tableName(request.tableName())
                .hostId(hostUserId)
                .invitedUserId(invitedUserId)
                .expiresIn(60)
                .build();

        messagingTemplate.convertAndSendToUser(
                invitedUserId.toString(),
                "/queue/invite",
                Map.of(
                        "type", "GAME_INVITE",
                        "data", inviteData
                )
        );

        log.info("Invite sent: table={}, host={}, invited={}", tableId, hostUserId, invitedUserId);
    }

    @MessageMapping("/table/invite/accept")
    public void acceptInvite(@Payload Map<String, String> payload, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UUID tableId = UUID.fromString(payload.get("tableId"));

        if (!inviteService.inviteExists(tableId, userId)) {
            sendErrorToUser(userId, "Invite not found or expired");
            return;
        }

        inviteService.removeInvite(tableId, userId);

        try {
            tablePlayerService.addAcceptedPlayer(tableId, userId);
            log.info("Invite accepted: table={}, user={}", tableId, userId);
        } catch (Exception e) {
            log.error("Error accepting invite: table={}, user={}, error={}", tableId, userId, e.getMessage(), e);
            sendErrorToUser(userId, "Failed to accept invite: " + e.getMessage());
        }
    }

    @MessageMapping("/table/invite/reject")
    public void rejectInvite(@Payload Map<String, String> payload, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UUID tableId = UUID.fromString(payload.get("tableId"));

        inviteService.removeInvite(tableId, userId);

        messagingTemplate.convertAndSend(
                "/topic/table/" + tableId + "/invite-rejected",
                Map.of("type", "INVITE_REJECTED", "userId", userId.toString())
        );

        log.info("Invite rejected: table={}, user={}", tableId, userId);
    }

    @MessageMapping("/table/ready")
    public void togglePlayerReady(@Payload Map<String, Object> payload, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UUID tableId = UUID.fromString((String) payload.get("tableId"));
        boolean ready = (Boolean) payload.get("ready");

        log.info("Received ready toggle: table={}, user={}, ready={}", tableId, userId, ready);

        try {
            // TODO: Implementar lógica no TablePlayerService
            // tablePlayerService.setPlayerReady(tableId, userId, ready);

            log.info("Player ready status would be updated: table={}, user={}, ready={}", tableId, userId, ready);

            messagingTemplate.convertAndSend(
                    "/topic/table/" + tableId + "/ready",
                    Map.of(
                            "type", "READY_STATUS_UPDATE",
                            "readyPlayers", ready ? List.of(userId.toString()) : List.of()
                    )
            );

        } catch (Exception e) {
            log.error("Error updating ready status: table={}, user={}, error={}", tableId, userId, e.getMessage(), e);
            sendErrorToUser(userId, "Failed to update ready status");
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