package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.identity.PersonalDataSource;
import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Each wallet with its balance and every movement, newest first, for the user's copy of their data.
 */
@Component
class WalletPersonalData implements PersonalDataSource {

    private static final int PAGE_SIZE = 100;

    private final WalletService wallets;

    WalletPersonalData(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public String section() {
        return "wallets";
    }

    @Override
    public Object collect(UUID userId) {
        return wallets.listWallets(userId).stream()
                .map(wallet -> new WalletData(wallet.id(), wallet.balance().currency().getCurrencyCode(),
                        wallet.status(), amount(wallet.balance()), wallet.createdAt(), movements(wallet.id(), userId)))
                .toList();
    }

    private List<MovementData> movements(UUID walletId, UUID userId) {
        List<MovementData> all = new ArrayList<>();
        WalletMovementPage page;
        int number = 0;
        do {
            page = wallets.movements(walletId, userId, MovementCriteria.none(), number++, PAGE_SIZE);
            page.items().forEach(item -> all.add(MovementData.from(item)));
        } while (page.hasNext());
        return all;
    }

    private static String amount(Money money) {
        return money.toDecimal().toPlainString();
    }

    record WalletData(UUID id, String currency, String status, String balance, Instant createdAt,
            List<MovementData> movements) {
    }

    record MovementData(UUID transactionId, String type, String direction, String amount, String balanceAfter,
            String reference, String counterparty, String description, Instant postedAt) {

        static MovementData from(WalletMovement item) {
            AccountMovement movement = item.movement();
            return new MovementData(movement.journalEntryId(), movement.type(), movement.direction().name(),
                    WalletPersonalData.amount(movement.amount()), WalletPersonalData.amount(movement.balanceAfter()), movement.reference(),
                    item.counterpartyName(), item.description(), movement.postedAt());
        }
    }
}
