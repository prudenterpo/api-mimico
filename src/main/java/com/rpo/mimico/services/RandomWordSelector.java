package com.rpo.mimico.services;

import com.rpo.mimico.entities.WordEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RandomWordSelector implements WordSelector {

    @Override
    public WordEntity pick(List<WordEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No words found for category");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
