package com.rpo.mimico.dtos;

import java.time.LocalDateTime;
import java.util.Set;

public record UserProfileDTO(
        String userId,
        String email,
        String nickname,
        String avatarUrl,
        Set<String> roles,
        LocalDateTime createdAt
) {}