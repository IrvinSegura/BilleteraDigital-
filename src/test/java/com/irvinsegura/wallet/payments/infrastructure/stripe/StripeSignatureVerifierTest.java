package com.irvinsegura.wallet.payments.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StripeSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";

    private final StripeSignatureVerifier verifier =
            new StripeSignatureVerifier("whsec_test", Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

    // Computed outside the code under test: HMAC-SHA256 of "<t>.<payload>" keyed with "whsec_test"
    @Test
    void acceptsAKnownStripeSignature() {
        String header = "t=1793534400,v1=a6db96dbc20973ae9a489a70c6f252b839a08912efb78be7fd1423daba44a736";

        assertThatCode(() -> verifier.verify(PAYLOAD, header)).doesNotThrowAnyException();
        assertThat(verifier.header(PAYLOAD, NOW.getEpochSecond())).isEqualTo(header);
    }

    @Test
    void acceptsAnyOfSeveralSignaturesWhileASecretIsRolled() {
        long timestamp = NOW.getEpochSecond();
        String valid = verifier.header(PAYLOAD, timestamp).split(",")[1];

        assertThatCode(() -> verifier.verify(PAYLOAD, "t=" + timestamp + ",v1=" + "ab".repeat(32) + "," + valid
                + ",v0=ignored")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAChangedBody() {
        String header = verifier.header(PAYLOAD, NOW.getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(PAYLOAD.replace("evt_1", "evt_2"), header))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("Signature does not match");
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() {
        StripeSignatureVerifier other =
                new StripeSignatureVerifier("whsec_other", Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> verifier.verify(PAYLOAD, other.header(PAYLOAD, NOW.getEpochSecond())))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void rejectsAReplayOutsideTheTolerance() {
        long sixMinutesAgo = NOW.minus(Duration.ofMinutes(6)).getEpochSecond();

        assertThatThrownBy(() -> verifier.verify(PAYLOAD, verifier.header(PAYLOAD, sixMinutesAgo)))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("Signature is too old");
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, null)).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "v1=abc")).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "t=abc,v1=abc"))
                .isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> verifier.verify(PAYLOAD, "t=" + NOW.getEpochSecond() + ",v1=not-hex"))
                .isInstanceOf(InvalidWebhookException.class);
    }
}
