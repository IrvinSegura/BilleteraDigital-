package com.irvinsegura.wallet.accounts.domain.port;

import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

    Optional<Wallet> findById(UUID id);

    /**
     * Loads the wallet with an exclusive row lock, taken by operations that take money out and by freezes.
     */
    Optional<Wallet> findByIdForUpdate(UUID id);

    /**
     * Loads the wallet with a shared row lock, taken by operations that bring money in. Several of them can hold it
     * at once, but a freeze waits for them and they wait for a freeze.
     */
    Optional<Wallet> findByIdForShare(UUID id);

    List<Wallet> findByOwner(UUID ownerId);

    Optional<Wallet> findByOwnerAndCurrency(UUID ownerId, Currency currency);

    boolean existsByOwnerAndCurrency(UUID ownerId, Currency currency);

    /**
     * Throws {@code WalletAlreadyExistsException} when the owner already has a wallet in that currency.
     */
    void save(Wallet wallet);
}
