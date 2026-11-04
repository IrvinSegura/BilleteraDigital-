package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    // The SHA-1 seed of RFC 6238's test vectors, "12345678901234567890", in Base32
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924",
            "2000000000, 279037"})
    void matchesTheRfcTestVectors(long epochSecond, String code) {
        // The RFC lists 8 digits; authenticator apps show the last 6
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(epochSecond))).isEqualTo(code);
    }

    @Test
    void base32RoundTripsAndEncodesTheRfcSeed() {
        byte[] seed = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.encode(seed)).isEqualTo(RFC_SECRET);
        assertThat(Totp.decode(RFC_SECRET)).isEqualTo(seed);
        assertThat(Totp.decode(Totp.newSecret())).hasSize(20);
    }

    @Test
    void acceptsOneStepOfClockDriftEitherWay() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);

        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, now.minusSeconds(30)), now)).isPresent();
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, now.plusSeconds(30)), now)).isPresent();
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, now.minusSeconds(90)), now)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, "12345", now)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, null, now)).isEmpty();
    }

    @Test
    void aCodeIsAcceptedOnceAndOlderOnesNever() {
        TwoFactor twoFactor = TwoFactor.start(new UserId(UUID.randomUUID()), "encrypted", Instant.EPOCH);
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String current = Totp.codeAt(RFC_SECRET, now);

        assertThat(twoFactor.acceptCode(RFC_SECRET, current, now)).isTrue();
        assertThat(twoFactor.acceptCode(RFC_SECRET, current, now)).isFalse();
        assertThat(twoFactor.acceptCode(RFC_SECRET, Totp.codeAt(RFC_SECRET, now.minusSeconds(30)), now)).isFalse();
        assertThat(twoFactor.acceptCode(RFC_SECRET, Totp.codeAt(RFC_SECRET, now.plusSeconds(30)), now)).isTrue();
    }

    @Test
    void recoveryCodesAreReadAsUsersTypeThem() {
        String code = RecoveryCodes.generate().getFirst();

        assertThat(code).matches("[a-z2-7]{5}-[a-z2-7]{5}");
        assertThat(RecoveryCodes.hash(code.toUpperCase().replace("-", " "))).isEqualTo(RecoveryCodes.hash(code));
        assertThat(RecoveryCodes.looksLikeRecoveryCode(code)).isTrue();
        assertThat(RecoveryCodes.looksLikeRecoveryCode("123456")).isFalse();
    }
}
