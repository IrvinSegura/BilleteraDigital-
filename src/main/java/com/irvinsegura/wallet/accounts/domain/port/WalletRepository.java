package com.irvinsegura.wallet.accounts.domain.port;

import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

    Optional<Wallet> findById(UUID id);

    List<Wallet> findByOwner(UUID ownerId);

    boolean existsByOwnerAndCurrency(UUID ownerId, Currency currency);

    /**
     * Throws {@code WalletAlreadyExistsException} when the owner already has a wallet in that currency.
     */
    void save(Wallet wallet);
}
