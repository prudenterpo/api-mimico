package com.rpo.mimico.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class GameClock {

    private GameClock() {
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
