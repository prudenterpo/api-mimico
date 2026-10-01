package com.rpo.mimico.controllers;

import com.rpo.mimico.domain.SignalKind;
import com.rpo.mimico.dtos.MediaSignalCommandDTO;
import com.rpo.mimico.services.MatchMediaService;
import com.rpo.mimico.services.MatchSignalingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchMediaWebSocketControllerTest {

    @Mock
    private MatchSignalingService matchSignalingService;
    @Mock
    private MatchMediaService matchMediaService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MatchMediaWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new MatchMediaWebSocketController(matchSignalingService, matchMediaService, messagingTemplate);
    }

    @Test
    void signalUsesTheWebSocketPrincipalAsSender() {
        UUID matchId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        MediaSignalCommandDTO command = new MediaSignalCommandDTO(recipientId, SignalKind.ANSWER, "opaque");
        Principal principal = () -> principalId.toString();

        controller.signal(matchId, command, principal);

        verify(matchSignalingService).signal(matchId, principalId, command);
    }

    @Test
    void mediaCommandsUseTheWebSocketPrincipal() {
        UUID matchId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        Principal principal = () -> principalId.toString();

        controller.mediaUnavailable(matchId, principal);
        controller.mediaAvailable(matchId, principal);

        verify(matchMediaService).reportUnavailable(matchId, principalId);
        verify(matchMediaService).reportAvailable(matchId, principalId);
    }
}
