package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.WalletNotFoundException;
import com.irvinsegura.wallet.accounts.WalletStatusChanged;
import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.model.WalletStatus;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository.WalletCounts;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository.WalletSlice;
import com.irvinsegura.wallet.ledger.Ledger;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator use cases. Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class WalletAdministrationService {

    private static final int MAX_SUGGESTIONS = 6;

    private final WalletRepository walletRepository;
    private final Ledger ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    WalletAdministrationService(WalletRepository walletRepository, Ledger ledger, ApplicationEventPublisher events,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminWalletView getWallet(UUID walletId) {
        return toView(walletRepository.findById(walletId).orElseThrow(WalletNotFoundException::new));
    }

    /**
     * A preview of the wallets whose id starts with what is being typed.
     */
    @Transactional(readOnly = true)
    public List<AdminWalletView> suggest(String idStart) {
        return walletRepository.findByIdStart(idStart.toLowerCase(Locale.ROOT), MAX_SUGGESTIONS).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Every wallet, the newest first, e.g. the frozen ones still waiting for a decision.
     *
     * @param status {@code ACTIVE} or {@code FROZEN}; null for both
     */
    @Transactional(readOnly = true)
    public AdminWalletPage listWallets(String status, int page, int size) {
        WalletSlice slice = walletRepository.findPage(status == null ? null : WalletStatus.valueOf(status), page,
                size);
        return new AdminWalletPage(slice.items().stream().map(this::toView).toList(), page, size, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public WalletCounts countWallets() {
        return walletRepository.count();
    }

    @Transactional(readOnly = true)
    public List<AdminWalletView> listWallets(UUID ownerId) {
        return walletRepository.findByOwner(ownerId).stream().map(this::toView).toList();
    }

    @Transactional
    public AdminWalletView freeze(UUID walletId, UUID adminId, String reason) {
        return changeStatus(walletId, adminId, reason, true);
    }

    @Transactional
    public AdminWalletView unfreeze(UUID walletId, UUID adminId, String reason) {
        return changeStatus(walletId, adminId, reason, false);
    }

    private AdminWalletView changeStatus(UUID walletId, UUID adminId, String reason, boolean freeze) {
        // Exclusive lock: waits for operations in flight, outgoing and incoming, and blocks the ones after it
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElseThrow(WalletNotFoundException::new);
        boolean changed = freeze ? wallet.freeze() : wallet.unfreeze();
        if (changed) {
            walletRepository.save(wallet);
            events.publishEvent(new WalletStatusChanged(wallet.id(), wallet.ownerId(), wallet.status().name(),
                    adminId, reason, clock.instant()));
        }
        return toView(wallet);
    }

    private AdminWalletView toView(Wallet wallet) {
        return new AdminWalletView(wallet.id(), wallet.ownerId(), ledger.balanceOf(wallet.ledgerAccountId()),
                wallet.status().name(), wallet.createdAt());
    }
}
