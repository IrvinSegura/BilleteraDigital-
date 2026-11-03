package com.irvinsegura.wallet.payments.domain.port;

import com.irvinsegura.wallet.payments.domain.model.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    void save(Payment payment);

    Optional<Payment> findById(UUID id);

    /**
     * Locks the row until the transaction ends, so a webhook and the stale payment job never apply the same change
     * twice.
     */
    Optional<Payment> findByIdForUpdate(UUID id);

    /**
     * Ids of payments registered before {@code createdBefore} that were never started.
     */
    List<UUID> findCreatedBefore(Instant createdBefore, int limit);

    /**
     * Ids of payments still waiting for the payer although their checkout expired before {@code expiredBefore}.
     */
    List<UUID> findPendingExpiredBefore(Instant expiredBefore, int limit);

    /**
     * The payment whose collected money has this provider id (a Stripe PaymentIntent).
     */
    Optional<Payment> findByProviderPaymentId(String provider, String providerPaymentId);

    /**
     * When the first payment through this provider was registered; nothing at the provider before it concerns us.
     */
    Optional<Instant> findEarliestCreatedAt(String provider);
}
