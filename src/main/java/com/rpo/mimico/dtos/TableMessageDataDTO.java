package com.rpo.mimico.dtos;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TableMessageDataDTO(
        UUID tableId,
        UUID senderUserId,
        String senderDisplayName,
        String message,
        OffsetDateTime sentAt
) {}
