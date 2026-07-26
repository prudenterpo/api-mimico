package com.rpo.mimico.dtos;

import java.time.OffsetDateTime;

public record RealtimeEventEnvelopeDTO<T>(
        String type,
        T data,
        OffsetDateTime occurredAt
) {}
