package com.irvinsegura.wallet.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void convertsDecimalAmountToMinorUnits() {
        Money money = Money.of(new BigDecimal("10.50"), "MXN");

        assertThat(money.minorUnits()).isEqualTo(1050);
        assertThat(money).hasToString("10.50 MXN");
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.505"), "MXN"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.ofMinor(-1, "MXN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAndSubtractsSameCurrency() {
        Money a = Money.ofMinor(1000, "MXN");
        Money b = Money.ofMinor(250, "MXN");

        assertThat(a.plus(b)).isEqualTo(Money.ofMinor(1250, "MXN"));
        assertThat(a.minus(b)).isEqualTo(Money.ofMinor(750, "MXN"));
        assertThat(a.isGreaterThanOrEqualTo(b)).isTrue();
    }

    @Test
    void subtractingMoreThanAvailableFails() {
        Money a = Money.ofMinor(100, "MXN");

        assertThatThrownBy(() -> a.minus(Money.ofMinor(101, "MXN")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOperationsBetweenCurrencies() {
        Money pesos = Money.ofMinor(100, "MXN");
        Money dollars = Money.ofMinor(100, "USD");

        assertThatThrownBy(() -> pesos.plus(dollars))
                .isInstanceOf(CurrencyMismatchException.class)
                .extracting("code").isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void additionOverflowFailsInsteadOfWrapping() {
        Money max = Money.ofMinor(Long.MAX_VALUE, "MXN");

        assertThatThrownBy(() -> max.plus(Money.ofMinor(1, "MXN")))
                .isInstanceOf(ArithmeticException.class);
    }
}
