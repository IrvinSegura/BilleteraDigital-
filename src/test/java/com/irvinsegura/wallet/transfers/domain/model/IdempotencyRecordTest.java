package com.irvinsegura.wallet.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    @Test
    void fingerprintDependsOnFieldBoundaries() {
        assertThat(IdempotencyRecord.fingerprint("ab", "c")).isNotEqualTo(IdempotencyRecord.fingerprint("a", "bc"));
        assertThat(IdempotencyRecord.fingerprint("a", null)).isNotEqualTo(IdempotencyRecord.fingerprint("a", ""));
        assertThat(IdempotencyRecord.fingerprint("a", "b")).isEqualTo(IdempotencyRecord.fingerprint("a", "b"));
    }

    @Test
    void sameRequestPassesAndDifferentRequestIsRejected() {
        IdempotencyRecord record = record("key-1", IdempotencyRecord.fingerprint("TRANSFER", "10"));

        assertThatCode(() -> record.requireSameRequest(IdempotencyRecord.fingerprint("TRANSFER", "10")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> record.requireSameRequest(IdempotencyRecord.fingerprint("TRANSFER", "11")))
                .isInstanceOf(IdempotencyKeyReusedException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void rejectsMalformedKeys() {
        assertThatThrownBy(() -> record("has spaces", "hash")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record("", "hash")).isInstanceOf(IllegalArgumentException.class);
    }

    private static IdempotencyRecord record(String key, String hash) {
        return new IdempotencyRecord(UUID.randomUUID(), key, hash, UUID.randomUUID(), Instant.now());
    }
}
