package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.LobbyMessageDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.services.OnlineUsersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LobbyWebSocketControllerTest {

    @Mock
    private OnlineUsersService onlineUsersService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private LobbyWebSocketController controller;
    private UUID userId;
    private Principal principal;

    @BeforeEach
    void setUp() {
        controller = new LobbyWebSocketController(onlineUsersService, messagingTemplate);
        userId = UUID.randomUUID();
        principal = () -> userId.toString();
    }

    @Test
    void joinLobbyAddsPresenceAndBroadcastsCanonicalEnvelope() {
        when(onlineUsersService.getOnlineUserProfiles()).thenReturn(List.of(
                new UserProfileDTO(userId.toString(), "player@example.test", "player_1", null, Set.of("PLAYER"), null)
        ));
        when(onlineUsersService.getOnlineCount()).thenReturn(1L);

        controller.joinLobby(principal);

        verify(onlineUsersService).addUser(userId);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/lobby/users"), captor.capture());

        RealtimeEventEnvelopeDTO<?> envelope = assertEnvelope(captor.getValue());
        assertEquals("ONLINE_USERS_UPDATED", envelope.type());
        assertTrue(envelope.data() instanceof Map<?, ?>);
    }

    @Test
    void handleLobbyChatPublishesCanonicalEnvelopeWithServerSenderIdentity() {
        when(onlineUsersService.getOnlineUserProfile(userId)).thenReturn(
                new UserProfileDTO(userId.toString(), "player@example.test", "player_1", null, Set.of("PLAYER"), null)
        );

        controller.handleLobbyChat(new LobbyMessageDTO("  hello lobby  "), principal);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/lobby/chat"), captor.capture());

        RealtimeEventEnvelopeDTO<?> envelope = assertEnvelope(captor.getValue());
        assertEquals("LOBBY_MESSAGE_POSTED", envelope.type());
        assertEquals("hello lobby", readRecordAccessor(envelope.data(), "message"));
        assertEquals(userId.toString(), readRecordAccessor(envelope.data(), "senderUserId"));
        assertEquals("player_1", readRecordAccessor(envelope.data(), "senderDisplayName"));
    }

    @Test
    void handleLobbyChatRejectsUnauthenticatedEmptyMalformedAndOverlongMessages() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.handleLobbyChat(new LobbyMessageDTO("hello"), null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handleLobbyChat(new LobbyMessageDTO("   "), principal));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handleLobbyChat(null, principal));
        assertThrows(IllegalArgumentException.class,
                () -> controller.handleLobbyChat(new LobbyMessageDTO("x".repeat(501)), principal));
    }

    private RealtimeEventEnvelopeDTO<?> assertEnvelope(Object value) {
        RealtimeEventEnvelopeDTO<?> envelope = assertInstanceOf(RealtimeEventEnvelopeDTO.class, value);
        assertTrue(envelope.occurredAt() != null);
        assertTrue(envelope.data() != null);
        return envelope;
    }

    private Object readRecordAccessor(Object target, String accessorName) {
        try {
            return target.getClass().getMethod(accessorName).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing record accessor " + accessorName, e);
        }
    }
}
