package com.irvinsegura.wallet.ledger;

import java.time.Instant;
import java.util.UUID;

public record RecordedEntry(UUID journalEntryId, Instant postedAt) {
}
