package com.irvinsegura.wallet.accounts.domain.port;

import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository {

    /**
     * Serializes changes to one owner's bank accounts until the transaction ends, so a count taken afterwards stays
     * true until commit.
     */
    void lockOwner(UUID ownerId);

    /**
     * @throws com.irvinsegura.wallet.accounts.domain.model.BankAccountAlreadyExistsException if the owner already
     *         has an active bank account with the same CLABE
     */
    void save(BankAccount bankAccount);

    Optional<BankAccount> findById(UUID id);

    /**
     * Active ones only, oldest first.
     */
    List<BankAccount> findActiveByOwner(UUID ownerId);
}
