package com.rpo.mimico.websocket;

import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.services.OnlineUsersService;
import com.rpo.mimico.services.ReconnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private static final String ONLINE_USERS_UPDATED = "ONLINE_USERS_UPDATED";

    private final OnlineUsersService onlineUsersService;
    private final ReconnectionService reconnectionService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            UUID userId = UUID.fromString(user.getName());

            onlineUsersService.addUser(userId);

            reconnectionService.handleReconnect(userId);

            broadcastOnlineUsers();

            log.info("User {} connected via WebSocket. Total online: {}",
                    userId, onlineUsersService.getOnlineCount());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            UUID userId = UUID.fromString(user.getName());

            onlineUsersService.removeUser(userId);

            reconnectionService.handleDisconnect(userId);

            broadcastOnlineUsers();

            log.info("User {} disconnected from WebSocket. Total online: {}",
                    userId, onlineUsersService.getOnlineCount());
        }
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
}
