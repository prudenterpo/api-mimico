package com.rpo.mimico.services;

import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class RandomDiceService implements DiceService {

    @Override
    public int roll() {
        return ThreadLocalRandom.current().nextInt(1, 7);
    }
}
