package com.rpo.mimico.services;

import com.rpo.mimico.domain.SignalKind;
import com.rpo.mimico.dtos.MediaSignalCommandDTO;
import com.rpo.mimico.dtos.MediaSignalDataDTO;
import com.rpo.mimico.dtos.RealtimeEventEnvelopeDTO;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchSignalingService {

    public static final int MAX_PAYLOAD_LENGTH = 20000;

    private static final Set<SignalKind> PAYLOAD_REQUIRED = EnumSet.of(
            SignalKind.OFFER,
            SignalKind.ANSWER,
            SignalKind.CANDIDATE
    );

    private final MatchPlayerRepository matchPlayerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void signal(UUID matchId, UUID fromUserId, MediaSignalCommandDTO command) {
        if (fromUserId == null || command == null || command.toUserId() == null || command.kind() == null) {
            throw new IllegalArgumentException("signal command is incomplete");
        }
        if (matchPlayerRepository.findByMatchIdAndUserId(matchId, fromUserId).isEmpty()) {
            throw new IllegalArgumentException("actor is not in match");
        }
        if (matchPlayerRepository.findByMatchIdAndUserId(matchId, command.toUserId()).isEmpty()) {
            throw new IllegalArgumentException("recipient is not in match");
        }

        String payload = command.payload();
        if (payload != null && payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("signal payload is too long");
        }
        if (PAYLOAD_REQUIRED.contains(command.kind()) && (payload == null || payload.isEmpty())) {
            throw new IllegalArgumentException("signal payload is required");
        }

        MediaSignalDataDTO data = new MediaSignalDataDTO(matchId, fromUserId, command.kind(), payload);
        messagingTemplate.convertAndSendToUser(
                command.toUserId().toString(),
                "/queue/match/" + matchId + "/signal",
                new RealtimeEventEnvelopeDTO<>("MEDIA_SIGNAL", data, OffsetDateTime.now())
        );
        log.info("Media signal delivered: match={}, from={}, to={}, kind={}",
                matchId, fromUserId, command.toUserId(), command.kind());
    }
}
