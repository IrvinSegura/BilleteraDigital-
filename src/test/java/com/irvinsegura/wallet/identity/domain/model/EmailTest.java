package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    void normalizesCaseAndSurroundingSpaces() {
        assertThat(Email.of("  Ana.Lopez@Example.COM ").value()).isEqualTo("ana.lopez@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ana", "ana@", "ana@example", "ana lopez@example.com", "a@b@example.com"})
    void rejectsMalformedAddresses(String raw) {
        assertThatThrownBy(() -> Email.of(raw))
                .isInstanceOf(InvalidEmailException.class)
                .extracting("code").isEqualTo("INVALID_EMAIL");
    }

    @Test
    void rejectsAddressesLongerThan254Characters() {
        String tooLong = "a".repeat(243) + "@example.com";

        assertThatThrownBy(() -> Email.of(tooLong)).isInstanceOf(InvalidEmailException.class);
    }
}
