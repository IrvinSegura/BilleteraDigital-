package com.irvinsegura.wallet.payments.domain.port;

import com.irvinsegura.wallet.payments.domain.model.ProviderBalanceSnapshot;
import java.util.List;

public interface ProviderBalanceSnapshotRepository {

    /**
     * Replaces the previous comparison for the same provider and currency.
     */
    void save(ProviderBalanceSnapshot snapshot);

    /**
     * Ordered by currency.
     */
    List<ProviderBalanceSnapshot> findByProvider(String provider);
}
