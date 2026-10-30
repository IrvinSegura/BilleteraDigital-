package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.WalletNotActiveException;
import com.irvinsegura.wallet.accounts.WalletNotFoundException;
import com.irvinsegura.wallet.accounts.WalletOpened;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.accounts.domain.model.DailyLimitPolicy;
import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.model.WalletAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.port.OutgoingUsageRepository;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.MovementPage;
import com.irvinsegura.wallet.ledger.UnsupportedCurrencyException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService implements Wallets {

    private final WalletRepository walletRepository;
    private final OutgoingUsageRepository outgoingUsageRepository;
    private final DailyLimitPolicy dailyLimitPolicy;
    private final Ledger ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    WalletService(WalletRepository walletRepository, OutgoingUsageRepository outgoingUsageRepository,
            DailyLimitPolicy dailyLimitPolicy, Ledger ledger, ApplicationEventPublisher events, Clock clock) {
        this.walletRepository = walletRepository;
        this.outgoingUsageRepository = outgoingUsageRepository;
        this.dailyLimitPolicy = dailyLimitPolicy;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public WalletSummary openWallet(UUID ownerId, String currencyCode) {
        Currency currency = parseCurrency(currencyCode);
        if (walletRepository.existsByOwnerAndCurrency(ownerId, currency)) {
            throw new WalletAlreadyExistsException(currency);
        }
        // Same transaction: if saving the wallet fails, the ledger account is rolled back with it
        Wallet wallet = Wallet.open(ownerId, currency, ledger.openUserAccount(currency), clock.instant());
        walletRepository.save(wallet);
        events.publishEvent(new WalletOpened(wallet.id(), ownerId, currency.getCurrencyCode(), wallet.createdAt()));
        return toSummary(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletSummary> listWallets(UUID ownerId) {
        return walletRepository.findByOwner(ownerId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public WalletSummary getWallet(UUID walletId, UUID ownerId) {
        return toSummary(requireOwned(walletId, ownerId));
    }

    @Transactional(readOnly = true)
    public MovementPage movements(UUID walletId, UUID ownerId, int page, int size) {
        return ledger.movementsOf(requireOwned(walletId, ownerId).ledgerAccountId(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletInfo getOwnedWallet(UUID walletId, UUID ownerId) {
        return toInfo(requireOwned(walletId, ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WalletInfo> findWallet(UUID ownerId, Currency currency) {
        return walletRepository.findByOwnerAndCurrency(ownerId, currency).map(WalletService::toInfo);
    }

    @Override
    @Transactional
    public WalletInfo reserveOutgoing(UUID walletId, UUID ownerId, Money amount) {
        // The row lock serializes every outgoing operation of this wallet, and also waits for a freeze in progress
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .filter(found -> found.isOwnedBy(ownerId))
                .orElseThrow(WalletNotFoundException::new);
        if (!wallet.isActive()) {
            throw new WalletNotActiveException();
        }
        LocalDate today = dailyLimitPolicy.dayOf(clock.instant());
        dailyLimitPolicy.check(outgoingUsageRepository.usedOn(walletId, today, wallet.currency()), amount);
        outgoingUsageRepository.add(walletId, today, amount);
        return toInfo(wallet);
    }

    static WalletInfo toInfo(Wallet wallet) {
        return new WalletInfo(wallet.id(), wallet.ownerId(), wallet.currency(), wallet.ledgerAccountId(),
                wallet.isActive());
    }

    private Wallet requireOwned(UUID walletId, UUID ownerId) {
        return walletRepository.findById(walletId)
                .filter(wallet -> wallet.isOwnedBy(ownerId))
                .orElseThrow(WalletNotFoundException::new);
    }

    private WalletSummary toSummary(Wallet wallet) {
        return new WalletSummary(wallet.id(), ledger.balanceOf(wallet.ledgerAccountId()), wallet.status().name(),
                wallet.createdAt());
    }

    private static Currency parseCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnsupportedCurrencyException(String.valueOf(currencyCode));
        }
    }
}
