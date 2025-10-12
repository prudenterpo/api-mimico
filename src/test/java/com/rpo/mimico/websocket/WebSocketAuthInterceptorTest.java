package com.rpo.mimico.websocket;

import com.rpo.mimico.securities.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private UUID userId;
    private String sessionId;
    private String validToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID().toString();
        validToken = "valid.jwt.token";
    }

    @Test
    void shouldAllowValidTokenWithValidSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + validToken);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        Claims claims = Jwts.claims()
                .subject(userId.toString())
                .add("sessionId", sessionId)
                .add("roles", List.of("USER"))
                .build();

        when(jwtTokenProvider.validateToken(validToken)).thenReturn(claims);
        when(jwtTokenProvider.extractRoles(validToken)).thenReturn(List.of("USER"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("session:" + userId)).thenReturn(sessionId);

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        verify(jwtTokenProvider).validateToken(validToken);
    }

    @Test
    void shouldRejectMissingAuthorizationHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () ->
                interceptor.preSend(message, channel)
        );
    }

    @Test
    void shouldRejectInvalidTokenFormat() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "InvalidFormat");
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        assertThrows(IllegalArgumentException.class, () ->
                interceptor.preSend(message, channel)
        );
    }

    @Test
    void shouldRejectExpiredSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + validToken);
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        Claims claims = Jwts.claims()
                .subject(userId.toString())
                .add("sessionId", sessionId)
                .build();

        when(jwtTokenProvider.validateToken(validToken)).thenReturn(claims);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("session:" + userId)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                interceptor.preSend(message, channel)
        );
    }

    @Test
    void shouldAllowNonConnectMessages() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        verifyNoInteractions(jwtTokenProvider);
    }
}