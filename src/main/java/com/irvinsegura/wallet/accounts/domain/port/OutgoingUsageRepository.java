package com.irvinsegura.wallet.accounts.domain.port;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/**
 * Running total of money that left each wallet per day. Callers must hold the wallet's row lock, so reading the
 * total and adding to it cannot interleave with another request.
 */
public interface OutgoingUsageRepository {

    Money usedOn(UUID walletId, LocalDate day, Currency currency);

    void add(UUID walletId, LocalDate day, Money amount);
}
