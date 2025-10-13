package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.Set;

@Builder
public record OnlineUsersResponseDTO(
        Set<String> users,
        Long count
) {}
