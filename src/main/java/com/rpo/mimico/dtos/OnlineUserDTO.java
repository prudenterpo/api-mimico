package com.rpo.mimico.dtos;

import java.io.Serializable;
import java.util.UUID;

public record OnlineUserDTO(
        UUID id,
        String nickname,
        String email,
        boolean isOnline
) implements Serializable {}