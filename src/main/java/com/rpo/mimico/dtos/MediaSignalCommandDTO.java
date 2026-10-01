package com.rpo.mimico.dtos;

import com.rpo.mimico.domain.SignalKind;

import java.util.UUID;

public record MediaSignalCommandDTO(
        UUID toUserId,
        SignalKind kind,
        String payload
) {}
