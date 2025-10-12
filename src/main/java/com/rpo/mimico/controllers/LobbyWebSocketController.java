package com.rpo.mimico.controllers;

import com.rpo.mimico.services.OnlineUsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LobbyWebSocketController {

    private final OnlineUsersService onlineUsersService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/lobby/join")
    public void joinLobby(Principal principal) {
        if (principal != null) {
            log.debug("User {} requested lobby state", principal.getName());
            broadcastOnlineUsers();
        }
    }

    private void broadcastOnlineUsers() {
        Map<String, Object> payload = Map.of(
                "type", "ONLINE_USERS_UPDATE",
                "users", onlineUsersService.getOnlineUsers(),
                "count", onlineUsersService.getOnlineCount()
        );

        messagingTemplate.convertAndSend("/topic/lobby/users", payload);
    }
}