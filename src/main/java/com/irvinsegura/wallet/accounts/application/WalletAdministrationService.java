package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.WalletNotFoundException;
import com.irvinsegura.wallet.accounts.WalletStatusChanged;
import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import com.irvinsegura.wallet.ledger.Ledger;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator use cases. Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class WalletAdministrationService {

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
