package com.rpo.mimico.websocket;

import com.rpo.mimico.services.OnlineUsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventLIstener {

    private final OnlineUsersService onlineUsersService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            UUID userId = UUID.fromString(user.getName());

            onlineUsersService.addUser(userId);

            broadcastOnlineUsers();

            log.info("User {} connected via WebSocket. Total online: {}",
                    userId, onlineUsersService.getOnlineUsers());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();

        if (user != null) {
            UUID userId = UUID.fromString(user.getName());

            onlineUsersService.removeUser(userId);

            broadcastOnlineUsers();

            log.info("User {} disconnected from WebSocket. Total online: {}",
                    userId, onlineUsersService.getOnlineCount());
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
