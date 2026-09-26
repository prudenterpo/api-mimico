package com.rpo.mimico.services;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SystemTimerService implements TimerService {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
