package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.LobbyMessageDTO;
import com.rpo.mimico.dtos.OnlineUserDTO;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.services.OnlineUsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @MessageMapping("/lobby/chat")
    public void handleLobbyChat(@Payload LobbyMessageDTO message, Principal principal) {
        if (principal != null) {
            log.info("Lobby chat: user={}, message='{}'", message.userName(), message.message());

            messagingTemplate.convertAndSend("/topic/lobby/chat", message);
        }
    }

    private void broadcastOnlineUsers() {
        List<UserEntity> onlineUsers = onlineUsersService.getOnlineUsersWithDetails();

        List<OnlineUserDTO> userDtos = onlineUsers.stream()
                .map(user -> new OnlineUserDTO(
                        user.getId(),
                        user.getNickname(),
                        "",
                        true
                ))
                .toList();

        Map<String, Object> payload = Map.of(
                "type", "ONLINE_USERS_UPDATE",
                "users", userDtos,
                "count", onlineUsersService.getOnlineCount()
        );

        messagingTemplate.convertAndSend("/topic/lobby/users", payload);
    }
}