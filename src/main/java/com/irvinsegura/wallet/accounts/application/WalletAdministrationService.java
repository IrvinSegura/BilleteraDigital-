package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.WalletNotFoundException;
import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import com.irvinsegura.wallet.ledger.Ledger;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator use cases. Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class WalletAdministrationService {

    private final WalletRepository walletRepository;
    private final Ledger ledger;

    WalletAdministrationService(WalletRepository walletRepository, Ledger ledger) {
        this.walletRepository = walletRepository;
        this.ledger = ledger;
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
        // Locked like outgoing operations: a freeze waits for in-flight ones and blocks the ones after it
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElseThrow(WalletNotFoundException::new);
        boolean changed = freeze ? wallet.freeze() : wallet.unfreeze();
        if (changed) {
            walletRepository.save(wallet);
        }
        return toView(wallet);
    }

    private AdminWalletView toView(Wallet wallet) {
        return new AdminWalletView(wallet.id(), wallet.ownerId(), ledger.balanceOf(wallet.ledgerAccountId()),
                wallet.status().name(), wallet.createdAt());
    }
}
