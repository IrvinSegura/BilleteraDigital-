package com.irvinsegura.wallet.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private final UUID sender = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    @Test
    void blankDescriptionsAreStoredAsNone() {
        assertThat(transfer(recipient, Money.ofMinor(100, "MXN"), "   ").description()).isNull();
        assertThat(transfer(recipient, Money.ofMinor(100, "MXN"), " Dinner ").description()).isEqualTo("Dinner");
    }

    @Test
    void senderAndRecipientMustDiffer() {
        assertThatThrownBy(() -> transfer(sender, Money.ofMinor(100, "MXN"), null))
                .isInstanceOf(SelfTransferException.class)
                .extracting("code").isEqualTo("SELF_TRANSFER");
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> transfer(recipient, Money.ofMinor(0, "MXN"), null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void descriptionIsLimitedTo140Characters() {
        assertThatThrownBy(() -> transfer(recipient, Money.ofMinor(100, "MXN"), "x".repeat(141)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Transfer transfer(UUID to, Money amount, String description) {
        return Transfer.record(UUID.randomUUID(), sender, UUID.randomUUID(), to, "bob@example.com",
                UUID.randomUUID(), amount, description, UUID.randomUUID(), NOW);
    }
}
