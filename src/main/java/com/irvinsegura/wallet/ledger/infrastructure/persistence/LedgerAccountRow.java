package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.AccountType;
import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Query projection; public because Hibernate instantiates it from a JPQL constructor expression.
 */
public record LedgerAccountRow(UUID id, AccountType type, String currency, long balanceMinor, String systemCode,
        Instant createdAt, Long version) {

    LedgerAccount toDomain() {
        return LedgerAccount.restore(id, type, Money.ofMinor(balanceMinor, currency), systemCode, createdAt,
                version);
    }
}
