package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.AssignTeamsRequestDTO;
import com.rpo.mimico.dtos.InviteDecisionRequestDTO;
import com.rpo.mimico.dtos.InvitePlayerRequestDTO;
import com.rpo.mimico.dtos.LeaveTableRequestDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.dtos.TableMessageDTO;
import com.rpo.mimico.services.OnlineUsersService;
import com.rpo.mimico.services.TablePlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TableWebSocketController {

    private final OnlineUsersService onlineUsersService;
    private final TablePlayerService tablePlayerService;

    @MessageMapping("/table/invite")
    public void sendInvite(@Payload InvitePlayerRequestDTO request, Principal principal) {
        requirePrincipal(principal);
        UUID hostUserId = UUID.fromString(principal.getName());
        UUID invitedUserId = request.invitedUserId();
        if (!onlineUsersService.isUserOnline(invitedUserId)) {
            throw new IllegalArgumentException("Invited user is offline");
        }
        tablePlayerService.sendInvite(request.tableId(), hostUserId, invitedUserId);
    }

    @MessageMapping("/table/invite/accept")
    public void acceptInvite(@Payload InviteDecisionRequestDTO request, Principal principal) {
        requirePrincipal(principal);
        tablePlayerService.acceptInvite(request.tableId(), request.inviteId(), UUID.fromString(principal.getName()));
    }

    @MessageMapping("/table/invite/reject")
    public void rejectInvite(@Payload InviteDecisionRequestDTO request, Principal principal) {
        requirePrincipal(principal);
        tablePlayerService.rejectInvite(request.tableId(), request.inviteId(), UUID.fromString(principal.getName()));
    }

    @MessageMapping("/table/{tableId}/chat")
    public void sendTableChat(
            @DestinationVariable UUID tableId,
            @Payload TableMessageDTO request,
            Principal principal
    ) {
        requirePrincipal(principal);
        tablePlayerService.postTableMessage(tableId, UUID.fromString(principal.getName()), request.message());
    }

    @MessageMapping("/table/teams/assign")
    public void assignTeams(@Payload AssignTeamsRequestDTO request, Principal principal) {
        requirePrincipal(principal);
        tablePlayerService.assignTeams(request.tableId(), UUID.fromString(principal.getName()), request.teamAssignments());
    }

    @MessageMapping("/table/match/start")
    public void startMatch(@Payload StartMatchRequestDTO request, Principal principal) {
        requirePrincipal(principal);
        tablePlayerService.startMatch(request.tableId(), UUID.fromString(principal.getName()), request.teamAssignments());
    }

    @MessageMapping("/table/leave")
    public void leaveTable(@Payload LeaveTableRequestDTO request, Principal principal) {
        requirePrincipal(principal);
        tablePlayerService.leaveTable(request.tableId(), UUID.fromString(principal.getName()));
    }

    private void requirePrincipal(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
    }
}
