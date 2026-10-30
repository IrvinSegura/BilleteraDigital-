package com.irvinsegura.wallet.ledger.domain.port;

import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository {

    Optional<LedgerAccount> findById(UUID id);

    /**
     * Loads and row-locks the accounts in ascending id order. Every caller locking in the same order is what
     * prevents deadlocks between concurrent entries.
     */
    List<LedgerAccount> findAllForUpdate(Collection<UUID> ids);

    Optional<LedgerAccount> findSystemAccount(String systemCode, Currency currency);

    void save(LedgerAccount account);
}
