package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.LobbyMessageDataDTO;
import com.rpo.mimico.dtos.LobbyMessageDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.services.OnlineUsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LobbyWebSocketController {

    private static final int LOBBY_MESSAGE_MAX_LENGTH = 500;
    private static final String ONLINE_USERS_UPDATED = "ONLINE_USERS_UPDATED";
    private static final String LOBBY_MESSAGE_POSTED = "LOBBY_MESSAGE_POSTED";

    private final OnlineUsersService onlineUsersService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/lobby/join")
    public void joinLobby(Principal principal) {
        UUID userId = requireUserId(principal);

        onlineUsersService.addUser(userId);
        log.debug("User {} joined lobby", userId);
        broadcastOnlineUsers();
    }

    @MessageMapping("/lobby/chat")
    public void handleLobbyChat(@Payload LobbyMessageDTO message, Principal principal) {
        UUID userId = requireUserId(principal);
        String text = validateMessage(message);

        UserProfileDTO sender = onlineUsersService.getOnlineUserProfile(userId);
        OffsetDateTime sentAt = OffsetDateTime.now();
        LobbyMessageDataDTO data = new LobbyMessageDataDTO(
                sender.userId(),
                sender.nickname(),
                text,
                sentAt
        );

        log.info("Lobby chat accepted: user={}, message='{}'", userId, text);

        messagingTemplate.convertAndSend(
                "/topic/lobby/chat",
                new RealtimeEventEnvelopeDTO<>(LOBBY_MESSAGE_POSTED, data, sentAt)
        );
    }

    private void broadcastOnlineUsers() {
        List<UserProfileDTO> onlineUsers = onlineUsersService.getOnlineUserProfiles();

        Map<String, Object> payload = Map.of(
                "users", onlineUsers,
                "count", onlineUsersService.getOnlineCount()
        );

        messagingTemplate.convertAndSend(
                "/topic/lobby/users",
                new RealtimeEventEnvelopeDTO<>(ONLINE_USERS_UPDATED, payload, OffsetDateTime.now())
        );
    }

    private UUID requireUserId(Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        return UUID.fromString(principal.getName());
    }

    private String validateMessage(LobbyMessageDTO message) {
        if (message == null || message.message() == null) {
            throw new IllegalArgumentException("Lobby message is required");
        }

        String text = message.message().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Lobby message is required");
        }
        if (text.length() > LOBBY_MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("Lobby message must be at most 500 characters");
        }
        return text;
    }
}
