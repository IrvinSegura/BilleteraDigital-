package com.irvinsegura.wallet.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepositTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    private final Deposit deposit = Deposit.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Money.ofMinor(5000, "MXN"), NOW);

    @Test
    void startsPendingWithNothingCredited() {
        assertThat(deposit.isPending()).isTrue();
        assertThat(deposit.balanceAfter()).isNull();
        assertThat(deposit.journalEntryId()).isNull();
        assertThat(deposit.closedAt()).isNull();
    }

    @Test
    void completingKeepsTheBalanceAndDropsTheCheckout() {
        deposit.awaitPayment("https://checkout.example/cs_1", NOW.plusSeconds(1800));
        UUID entryId = UUID.randomUUID();

        deposit.complete(Money.ofMinor(7500, "MXN"), entryId, NOW.plusSeconds(60));

        assertThat(deposit.status()).isEqualTo(DepositStatus.COMPLETED);
        assertThat(deposit.balanceAfter()).isEqualTo(Money.ofMinor(7500, "MXN"));
        assertThat(deposit.journalEntryId()).isEqualTo(entryId);
        assertThat(deposit.checkoutUrl()).isNull();
        assertThat(deposit.closedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void aRejectionKeepsWhyTheMoneyWasRefused() {
        deposit.reject("WALLET_NOT_ACTIVE", NOW);

        assertThat(deposit.status()).isEqualTo(DepositStatus.REJECTED);
        assertThat(deposit.rejectionReason()).isEqualTo("WALLET_NOT_ACTIVE");
    }

    @Test
    void abandoningOnlyEndsAPendingDeposit() {
        assertThat(deposit.abandon(DepositStatus.EXPIRED, NOW)).isTrue();
        assertThat(deposit.abandon(DepositStatus.FAILED, NOW)).isFalse();

        assertThat(deposit.status()).isEqualTo(DepositStatus.EXPIRED);
        assertThatThrownBy(() -> deposit.complete(Money.ofMinor(5000, "MXN"), UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canOnlyBeAbandonedAsExpiredOrFailed() {
        assertThatThrownBy(() -> deposit.abandon(DepositStatus.COMPLETED, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
