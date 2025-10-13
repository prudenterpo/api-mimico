package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.InvitePlayerRequestDTO;
import com.rpo.mimico.dtos.InviteResponseDTO;
import com.rpo.mimico.services.InviteService;
import com.rpo.mimico.services.OnlineUsersService;
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
public class TableWebSocketController {

    private final InviteService inviteService;
    private final OnlineUsersService onlineUsersService;
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

        // TODO: Add user to table (Phase 3)

        log.info("Invite accepted: table={}, user={}", tableId, userId);
    }

    @MessageMapping("/table/invite/reject")
    public void rejectInvite(@Payload Map<String, String> payload, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UUID tableId = UUID.fromString(payload.get("tableId"));

        inviteService.removeInvite(tableId, userId);

        log.info("Invite rejected: table={}, user={}", tableId, userId);
    }

    private void sendErrorToUser(UUID userId, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/error",
                Map.of("message", message)
        );
    }
}