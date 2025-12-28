package com.rpo.mimico.dtos;

import java.util.UUID;

public record TablePlayerDTO(
   UUID userId,
   String nickname,
   String status
) {}
