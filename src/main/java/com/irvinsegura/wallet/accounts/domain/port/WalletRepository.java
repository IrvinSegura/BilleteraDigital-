package com.irvinsegura.wallet.accounts.domain.port;

import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

    Optional<Wallet> findById(UUID id);

    /**
     * Loads the wallet with a row lock. Only the wallet money leaves is locked, so two opposite transfers lock
     * different rows and cannot deadlock here.
     */
    Optional<Wallet> findByIdForUpdate(UUID id);

    List<Wallet> findByOwner(UUID ownerId);

    Optional<Wallet> findByOwnerAndCurrency(UUID ownerId, Currency currency);

    boolean existsByOwnerAndCurrency(UUID ownerId, Currency currency);

    /**
     * Throws {@code WalletAlreadyExistsException} when the owner already has a wallet in that currency.
     */
    void save(Wallet wallet);
}
