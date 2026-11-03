package com.irvinsegura.wallet.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PaymentTransitionsTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Currency MXN = Currency.getInstance("MXN");
    private static final Money AMOUNT = Money.ofMinor(4_000, "MXN");

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final Ledger ledger = mock(Ledger.class);
    private final PaymentTransitions transitions = new PaymentTransitions(repository, gateway, ledger,
            mock(ApplicationEventPublisher.class), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID paymentId = UUID.randomUUID();
    private final UUID clearing = UUID.randomUUID();
    private final UUID settlement = UUID.randomUUID();
    private final UUID unapplied = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Payment refunding = Payment.restore(paymentId, UUID.randomUUID(), AMOUNT, "Deposit", "STRIPE",
                PaymentStatus.REFUND_PENDING, "cs_1", null, null, "pi_1", null, UUID.randomUUID(), NOW, NOW, 1L);
        when(repository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(refunding));
        when(gateway.holdsFunds()).thenReturn(true);
        when(ledger.providerClearingAccount(MXN)).thenReturn(clearing);
        when(ledger.settlementAccount(MXN)).thenReturn(settlement);
        when(ledger.unappliedPaymentsAccount(MXN)).thenReturn(unapplied);
        when(ledger.record(any())).thenReturn(new RecordedEntry(UUID.randomUUID(), NOW));
    }

    @Test
    void aRefundLeavesTheProviderBalance() {
        when(ledger.balanceOf(clearing)).thenReturn(AMOUNT);

        transitions.markRefunded(paymentId, "re_1");

        assertThat(recordedEntry().creditAccountId()).isEqualTo(clearing);
        assertThat(recordedEntry().debitAccountId()).isEqualTo(unapplied);
    }

    @Test
    void aRefundTheProviderBalanceCannotCoverComesFromTheBank() {
        when(ledger.balanceOf(clearing)).thenReturn(Money.ofMinor(1_000, "MXN"));

        transitions.markRefunded(paymentId, "re_1");

        assertThat(recordedEntry().creditAccountId()).isEqualTo(settlement);
    }

    private NewJournalEntry recordedEntry() {
        ArgumentCaptor<NewJournalEntry> entry = ArgumentCaptor.forClass(NewJournalEntry.class);
        verify(ledger).record(entry.capture());
        return entry.getValue();
    }
}
