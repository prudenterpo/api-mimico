package com.rpo.mimico.services;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rpo.mimico.domain.SignalKind;
import com.rpo.mimico.dtos.MediaSignalCommandDTO;
import com.rpo.mimico.dtos.MediaSignalDataDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchSignalingServiceTest {

    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MatchSignalingService service;
    private UUID matchId;
    private UUID senderId;
    private UUID recipientId;
    private UUID outsiderId;

    @BeforeEach
    void setUp() {
        service = new MatchSignalingService(matchPlayerRepository, messagingTemplate);
        matchId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        outsiderId = UUID.randomUUID();
    }

    @Test
    void offerIsDeliveredOnlyToTheTargetMemberAndIsNotLogged() {
        String payload = "sdp-secret-do-not-log-9f3a";
        member(senderId);
        member(recipientId);
        ListAppender<ILoggingEvent> logs = attachLogs();

        try {
            service.signal(matchId, senderId, new MediaSignalCommandDTO(recipientId, SignalKind.OFFER, payload));
        } finally {
            detachLogs(logs);
        }

        ArgumentCaptor<Object> envelopeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(recipientId.toString()),
                eq("/queue/match/" + matchId + "/signal"),
                envelopeCaptor.capture()
        );
        UUID anotherMemberId = UUID.randomUUID();
        verify(messagingTemplate, never()).convertAndSendToUser(eq(senderId.toString()), anyString(), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq(anotherMemberId.toString()), anyString(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        RealtimeEventEnvelopeDTO<?> envelope = (RealtimeEventEnvelopeDTO<?>) envelopeCaptor.getValue();
        MediaSignalDataDTO data = (MediaSignalDataDTO) envelope.data();
        assertEquals("MEDIA_SIGNAL", envelope.type());
        assertEquals(matchId, data.matchId());
        assertEquals(senderId, data.fromUserId());
        assertEquals(SignalKind.OFFER, data.kind());
        assertEquals(payload, data.payload());
        assertNotNull(envelope.occurredAt());

        String written = logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertFalse(written.contains(payload));
        assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(payload)));
    }

    @Test
    void nonMemberSignalIsRejectedAndNobodyReceivesIt() {
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, outsiderId)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.signal(
                        matchId,
                        outsiderId,
                        new MediaSignalCommandDTO(recipientId, SignalKind.OFFER, "secret")
                )
        );

        assertEquals("actor is not in match", error.getMessage());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void payloadLongerThan20000CharactersIsRejected() {
        member(senderId);
        member(recipientId);
        String payload = "x".repeat(MatchSignalingService.MAX_PAYLOAD_LENGTH + 1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.signal(matchId, senderId, new MediaSignalCommandDTO(recipientId, SignalKind.CANDIDATE, payload))
        );

        assertEquals("signal payload is too long", error.getMessage());
        assertFalse(error.getMessage().contains(payload));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void joinMayOmitPayloadAndStillStaysOffSharedTopics() {
        member(senderId);
        member(recipientId);

        service.signal(matchId, senderId, new MediaSignalCommandDTO(recipientId, SignalKind.JOIN, null));

        verify(messagingTemplate).convertAndSendToUser(
                eq(recipientId.toString()),
                eq("/queue/match/" + matchId + "/signal"),
                any()
        );
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    private void member(UUID userId) {
        MatchPlayerEntity player = new MatchPlayerEntity();
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, userId)).thenReturn(Optional.of(player));
    }

    private ListAppender<ILoggingEvent> attachLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(MatchSignalingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogs(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(MatchSignalingService.class);
        logger.detachAppender(appender);
    }
}
