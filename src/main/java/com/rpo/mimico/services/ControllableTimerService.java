package com.rpo.mimico.services;

import java.time.Duration;
import java.time.Instant;

public class ControllableTimerService implements TimerService {

    private Instant current;

    public ControllableTimerService(Instant start) {
        this.current = start;
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }

    @Override
    public Instant now() {
        return current;
    }
}
