package com.irvinsegura.wallet.accounts.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClabeTest {

    // A published example of a valid CLABE (bank 032)
    private static final String VALID = "032180000118359719";

    @Test
    void acceptsAValidClabeWrittenWithSpacesOrDashes() {
        Clabe clabe = Clabe.parse("032 180 00011835971-9");

        assertThat(clabe.value()).isEqualTo(VALID);
        assertThat(clabe.bankCode()).isEqualTo("032");
        assertThat(clabe.lastFour()).isEqualTo("9719");
    }

    @Test
    void rejectsATypo() {
        // Two digits swapped: the check digit no longer matches
        assertThatThrownBy(() -> Clabe.parse("032180000118357919"))
                .isInstanceOf(InvalidClabeException.class)
                .hasMessageContaining("check digit");
    }

    @Test
    void rejectsAnythingButEighteenDigits() {
        assertThatThrownBy(() -> Clabe.parse("03218000011835971")).isInstanceOf(InvalidClabeException.class);
        assertThatThrownBy(() -> Clabe.parse("0321800001183597190")).isInstanceOf(InvalidClabeException.class);
        assertThatThrownBy(() -> Clabe.parse("03218000011835971A")).isInstanceOf(InvalidClabeException.class);
        assertThatThrownBy(() -> Clabe.parse(null)).isInstanceOf(InvalidClabeException.class);
    }

    @Test
    void neverPrintsTheFullNumber() {
        assertThat(Clabe.parse(VALID)).hasToString("CLABE ending 9719");
    }
}
