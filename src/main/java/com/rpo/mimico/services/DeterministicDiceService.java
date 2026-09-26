package com.rpo.mimico.services;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class DeterministicDiceService implements DiceService {

    private final Queue<Integer> values;

    public DeterministicDiceService(Integer... values) {
        this.values = new ArrayDeque<>(List.of(values));
    }

    @Override
    public int roll() {
        Integer value = values.poll();
        if (value == null) {
            throw new IllegalStateException("No dice values left");
        }
        return value;
    }
}
