package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Adjustment;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdjustmentRepository {

    void insert(Adjustment adjustment);

    void update(Adjustment adjustment);

    Optional<Adjustment> findById(UUID id);

    /**
     * Locks the row until the transaction ends, so two administrators deciding at once can't both apply it.
     */
    Optional<Adjustment> findByIdForUpdate(UUID id);

    /**
     * Adjustments matching the filters, those waiting for approval oldest first, then the rest newest first.
     *
     * @param status   {@code null} for any
     * @param ticketId {@code null} for any
     */
    AdjustmentSlice page(AdjustmentStatus status, UUID ticketId, int page, int size);

    long countPending();

    record AdjustmentSlice(List<Adjustment> items, boolean hasNext) {
    }
}
