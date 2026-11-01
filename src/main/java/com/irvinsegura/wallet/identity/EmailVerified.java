package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user proves they own their address, which lets money leave their wallets.
 */
public record EmailVerified(UUID userId, Instant occurredAt) {
}
