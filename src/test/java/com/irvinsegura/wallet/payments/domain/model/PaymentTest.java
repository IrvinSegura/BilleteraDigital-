package com.irvinsegura.wallet.payments.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final Money AMOUNT = Money.ofMinor(10050, "MXN");

    private final Payment payment = Payment.register(UUID.randomUUID(), UUID.randomUUID(), AMOUNT,
            "Deposit to your MXN wallet", "STRIPE", NOW);

    @Test
    void anOpenCheckoutWaitsForThePayer() {
        payment.open(new CheckoutSession("cs_1", "https://checkout.example/cs_1", NOW.plus(Duration.ofMinutes(30)),
                null), NOW);

        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.isCollected()).isFalse();
        assertThat(payment.checkoutUrl()).isEqualTo("https://checkout.example/cs_1");
    }

    @Test
    void aCheckoutPaidOnCreationSucceedsRightAway() {
        payment.open(new CheckoutSession("sim_cs", null, null, "sim_pi"), NOW);

        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.providerPaymentId()).isEqualTo("sim_pi");
    }

    @Test
    void succeedingTwiceChangesNothingTheSecondTime() {
        open();

        assertThat(payment.succeed("pi_1", AMOUNT, NOW)).isTrue();
        assertThat(payment.succeed("pi_1", AMOUNT, NOW.plusSeconds(5))).isFalse();
        assertThat(payment.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aDifferentAmountIsNeverAccepted() {
        open();

        assertThatThrownBy(() -> payment.succeed("pi_1", Money.ofMinor(100, "MXN"), NOW))
                .isInstanceOf(PaymentAmountMismatchException.class);
        assertThatThrownBy(() -> payment.succeed("pi_1", Money.ofMinor(10050, "USD"), NOW))
                .isInstanceOf(PaymentAmountMismatchException.class);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void moneyCollectedAfterExpiringIsStillRecorded() {
        open();
        assertThat(payment.expire(NOW)).isTrue();

        // The caller must learn about it to give the money back
        assertThat(payment.succeed("pi_1", AMOUNT, NOW)).isTrue();
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void onlyPaymentsStillWaitingCanFailOrExpire() {
        assertThat(payment.fail(NOW)).isTrue();
        assertThat(payment.expire(NOW)).isFalse();
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void refundsOnlyCollectedMoneyAndOnlyOnce() {
        open();
        assertThat(payment.requestRefund(NOW)).isFalse();

        payment.succeed("pi_1", AMOUNT, NOW);
        assertThat(payment.requestRefund(NOW)).isTrue();
        assertThat(payment.requestRefund(NOW)).isFalse();
        assertThat(payment.markRefunded("re_1", NOW)).isTrue();
        assertThat(payment.markRefunded("re_2", NOW)).isFalse();

        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.providerRefundId()).isEqualTo("re_1");
        assertThat(payment.isCollected()).isTrue();
    }

    @Test
    void cannotBeStartedTwice() {
        open();

        assertThatThrownBy(this::open).isInstanceOf(IllegalStateException.class);
    }

    private void open() {
        payment.open(new CheckoutSession("cs_1", "https://checkout.example/cs_1", NOW.plus(Duration.ofMinutes(30)),
                null), NOW);
    }
}
