package com.rpo.mimico.dtos;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record TableResponseDTO(
        UUID id,
        String name,
        UUID hostId,
        String hostNickname,
        String status,
        LocalDateTime createdAt
) {}