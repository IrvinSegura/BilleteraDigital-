package com.irvinsegura.wallet.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

record SessionStartRow(UUID familyId, Instant startedAt) {
}
