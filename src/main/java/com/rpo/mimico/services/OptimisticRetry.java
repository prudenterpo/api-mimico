package com.rpo.mimico.services;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.function.Supplier;

public final class OptimisticRetry {

    public static final int MAX_ATTEMPTS = 3;

    private OptimisticRetry() {
    }

    public static <T> T run(Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                if (!isOptimisticLock(ex) || attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                last = ex;
            }
        }
        throw last;
    }

    static boolean isOptimisticLock(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException || current instanceof OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
