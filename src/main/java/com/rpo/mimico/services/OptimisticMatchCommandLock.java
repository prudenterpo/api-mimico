package com.rpo.mimico.services;

import com.rpo.mimico.exceptions.MatchNotFoundException;
import com.rpo.mimico.repositories.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class OptimisticMatchCommandLock implements MatchCommandLock {

    private final PlatformTransactionManager transactionManager;
    private final MatchRepository matchRepository;

    @Override
    public <T> T execute(UUID matchId, Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return OptimisticRetry.run(() -> template.execute(status -> {
            matchRepository.findForUpdate(matchId)
                    .orElseThrow(() -> new MatchNotFoundException(matchId));
            return action.get();
        }));
    }
}
