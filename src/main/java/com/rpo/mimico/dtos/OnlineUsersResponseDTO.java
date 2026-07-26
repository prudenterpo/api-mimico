package com.rpo.mimico.dtos;

import lombok.Builder;

import java.util.List;

@Builder
public record OnlineUsersResponseDTO(
        List<UserProfileDTO> users,
        Long count
) {}
