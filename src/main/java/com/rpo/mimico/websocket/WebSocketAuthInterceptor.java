package com.rpo.mimico.websocket;

import com.rpo.mimico.securities.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SESSION_KEY_PREFIX = "session:";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("WebSocket connection rejected: MIssing or invalid Authorization header");
                throw new IllegalArgumentException("Missing Authorization header");
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                authenticateWebSocketConnection(accessor, token);
            } catch (Exception e) {
                log.error("WebSocket authentication failed: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid authentication token", e);
            }
        }

        return message;
    }

    private void authenticateWebSocketConnection(StompHeaderAccessor accessor, String token) {
        Claims claims = jwtTokenProvider.validateToken(token);

        UUID userId = UUID.fromString(claims.getSubject());
        String sessionId = claims.get("sessionId", String.class);

        if (!isValidSession(userId.toString(), sessionId)) {
            log.warn("WebSocket connection rejected: Invalid or expired session for user {}", userId);
            throw new IllegalArgumentException("Invalid or expired session");
        }

        List<String> roles = jwtTokenProvider.extractRoles(token);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                authorities
        );

        accessor.setUser(authentication);
    }

    private boolean isValidSession(String userId, String sessionId) {
        String redisSessionKey = SESSION_KEY_PREFIX + userId;
        String currentSessionId = redisTemplate.opsForValue().get(redisSessionKey);

        return sessionId != null && sessionId.equals(currentSessionId);
    }
}
