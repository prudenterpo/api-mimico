package com.rpo.mimico.dtos;

import java.time.OffsetDateTime;

public record LobbyMessageDataDTO(
        String senderUserId,
        String senderDisplayName,
        String message,
        OffsetDateTime sentAt
) {}
