package com.irvinsegura.wallet.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WithdrawalTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    private final Withdrawal withdrawal = Withdrawal.request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), Money.ofMinor(10000, "MXN"), Money.ofMinor(5000, "MXN"), UUID.randomUUID(), NOW);

    @Test
    void startsPendingWithoutAPayout() {
        assertThat(withdrawal.isPending()).isTrue();
        assertThat(withdrawal.providerReference()).isNull();
        assertThat(withdrawal.settlementEntryId()).isNull();
    }

    @Test
    void recordingTheSamePayoutTwiceChangesNothing() {
        assertThat(withdrawal.markSent("po_1")).isTrue();
        assertThat(withdrawal.markSent("po_1")).isFalse();
        assertThat(withdrawal.isPending()).isTrue();
    }

    @Test
    void paidKeepsTheEntryThatPaidItOut() {
        UUID entryId = UUID.randomUUID();

        withdrawal.markPaid("po_1", entryId, NOW.plusSeconds(60));

        assertThat(withdrawal.status()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(withdrawal.settlementEntryId()).isEqualTo(entryId);
        assertThat(withdrawal.closedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(withdrawal.failureReason()).isNull();
    }

    @Test
    void aFailureKeepsWhyAndTheEntryThatGaveTheMoneyBack() {
        UUID entryId = UUID.randomUUID();

        withdrawal.fail("po_1", "ACCOUNT_CLOSED", entryId, NOW);

        assertThat(withdrawal.status()).isEqualTo(WithdrawalStatus.FAILED);
        assertThat(withdrawal.failureReason()).isEqualTo("ACCOUNT_CLOSED");
        assertThat(withdrawal.settlementEntryId()).isEqualTo(entryId);
    }

    @Test
    void isSettledOnlyOnce() {
        withdrawal.markPaid("po_1", UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> withdrawal.fail("po_1", "ACCOUNT_CLOSED", UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> withdrawal.markSent("po_2")).isInstanceOf(IllegalStateException.class);
    }
}
