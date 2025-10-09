package com.rpo.mimico.dtos;

public record RegisterResponseDTO(
        String userId,
        String email,
        String nickname,
        String message
) {}
