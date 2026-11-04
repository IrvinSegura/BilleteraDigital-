package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record RecentRecipientRow(UUID recipientId, String recipientEmail, Instant lastSentAt, Long timesSent) {
}
