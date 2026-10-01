package com.rpo.mimico.dtos;

import com.rpo.mimico.domain.SignalKind;

import java.util.UUID;

public record MediaSignalDataDTO(
        UUID matchId,
        UUID fromUserId,
        SignalKind kind,
        String payload
) {}
