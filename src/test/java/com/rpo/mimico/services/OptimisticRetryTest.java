package com.rpo.mimico.services;

import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptimisticRetryTest {

    @Test
    void retriesOptimisticLockThreeTimes() {
        AtomicInteger attempts = new AtomicInteger();

        String result = OptimisticRetry.run(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void givesUpAfterThreeOptimisticLockFailures() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(OptimisticLockException.class, () -> OptimisticRetry.run(() -> {
            attempts.incrementAndGet();
            throw new OptimisticLockException("conflict");
        }));
        assertEquals(OptimisticRetry.MAX_ATTEMPTS, attempts.get());
    }

    @Test
    void findForUpdateUsesOptimisticForceIncrement() throws Exception {
        Method method = com.rpo.mimico.repositories.MatchRepository.class.getMethod("findForUpdate", UUID.class);
        Lock lock = method.getAnnotation(Lock.class);
        assertEquals(LockModeType.OPTIMISTIC_FORCE_INCREMENT, lock.value());
    }
}
