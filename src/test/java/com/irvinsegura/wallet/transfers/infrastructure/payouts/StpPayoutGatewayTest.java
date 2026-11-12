package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StpPayoutGatewayTest {

    @Test
    void theTrackingKeyIsTheSameOnEveryAttemptAndFitsStp() {
        UUID withdrawalId = UUID.fromString("0b9c1f6e-3a52-4f0e-9d0a-6f1d2c3b4a59");
        LocalDate day = LocalDate.of(2026, 11, 9);

        String key = StpPayoutGateway.trackingKey(withdrawalId, day);

        assertThat(key).isEqualTo(StpPayoutGateway.trackingKey(withdrawalId, day));
        assertThat(key).hasSize(29).startsWith("BDW261109").matches("[A-Z0-9]+");
        assertThat(StpPayoutGateway.trackingKey(UUID.randomUUID(), day)).isNotEqualTo(key).hasSize(29);
    }

    @Test
    void theSpeiInstitutionComesFromTheClabe() {
        assertThat(StpPayoutGateway.institutionOf("012180015000000001")).isEqualTo("40012");
        assertThat(StpPayoutGateway.institutionOf("002180015000000001")).isEqualTo("40002");
        // Development banks and other participants
        assertThat(StpPayoutGateway.institutionOf("166180015000000001")).isEqualTo("37166");
        assertThat(StpPayoutGateway.institutionOf("646180015000000001")).isEqualTo("90646");
        assertThat(StpPayoutGateway.institutionOf("722969010000000001")).isEqualTo("90722");
    }

    @Test
    void speiTextHasNoAccentsOrSymbolsAndFitsThirtyNineCharacters() {
        assertThat(StpPayoutGateway.speiText("José Peña-Núñez")).isEqualTo("Jose Pena Nunez");
        assertThat(StpPayoutGateway.speiText("  María   O'Connor & Hijos, S.A. ")).isEqualTo("Maria O Connor Hijos S A");
        assertThat(StpPayoutGateway.speiText("A".repeat(50))).hasSize(39);
    }

    @Test
    void theOriginalStringKeepsEmptyPlaces() {
        assertThat(StpSigner.originalString(Arrays.asList("40012", "EMPRESA", "", null, "KEY")))
                .isEqualTo("||40012|EMPRESA|||KEY||");
    }

    @Test
    void theSignatureVerifiesWithThePublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        List<String> fields = List.of("EMPRESA", "BDW261109000000000000000000001", "E", "20261109");

        String signature = StpSigner.fromPem(pem).sign(fields);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keys.getPublic());
        verifier.update(StpSigner.originalString(fields).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(signature))).isTrue();
    }

    @Test
    void anEncryptedOrMissingKeyIsRefusedAtStartup() {
        assertThatThrownBy(() -> StpSigner.fromPem("-----BEGIN ENCRYPTED PRIVATE KEY-----\nabc\n"
                + "-----END ENCRYPTED PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl pkcs8");
        assertThatThrownBy(() -> StpSigner.fromPem("-----BEGIN PRIVATE KEY-----\nbm90IGEga2V5\n"
                + "-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class);
    }
}
