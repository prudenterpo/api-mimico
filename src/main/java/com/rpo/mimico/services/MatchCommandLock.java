package com.rpo.mimico.services;

import java.util.UUID;
import java.util.function.Supplier;

public interface MatchCommandLock {

    <T> T execute(UUID matchId, Supplier<T> action);
}
