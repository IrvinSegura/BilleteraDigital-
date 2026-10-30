package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.transfers.domain.model.IdempotencyKeyTakenException;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.port.IdempotencyRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a money-moving operation at most once per user and Idempotency-Key.
 * <p>
 * The key is inserted first, in the same transaction as the operation. A concurrent request with the same key blocks
 * on that insert; if the first one commits, the second gets a key violation and replays the stored result, and if
 * the first one fails, its key is rolled back with it and the retry runs normally. Failed requests are therefore
 * never cached.
 */
@Component
class IdempotentExecutor {

    private final IdempotencyRepository repository;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;
    private final Clock clock;

    IdempotentExecutor(IdempotencyRepository repository, PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * @param operation receives the id the new resource must use and performs the change
     * @param loader    loads a resource created earlier by that id, to replay it
     */
    <T> Idempotent<T> execute(UUID userId, String key, String requestHash, Function<UUID, T> operation,
            Function<UUID, T> loader) {
        try {
            return transaction.execute(status -> {
                Optional<IdempotencyRecord> existing = repository.find(userId, key);
                if (existing.isPresent()) {
                    return replay(existing.get(), requestHash, loader);
                }
                UUID resourceId = UUID.randomUUID();
                repository.claim(new IdempotencyRecord(userId, key, requestHash, resourceId, clock.instant()));
                return new Idempotent<>(operation.apply(resourceId), false);
            });
        } catch (IdempotencyKeyTakenException e) {
            // The losing transaction is already rolled back; read the winner's result in a fresh one
            return readOnlyTransaction.execute(status -> replay(
                    repository.find(userId, key).orElseThrow(), requestHash, loader));
        }
    }

    private static <T> Idempotent<T> replay(IdempotencyRecord record, String requestHash, Function<UUID, T> loader) {
        record.requireSameRequest(requestHash);
        return new Idempotent<>(loader.apply(record.resourceId()), true);
    }
}
