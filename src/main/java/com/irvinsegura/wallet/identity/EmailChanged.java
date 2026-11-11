package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the owner followed the link to their new address. Every session was ended; the old address is told,
 * so a takeover through a hijacked session does not go unnoticed.
 */
public record EmailChanged(UUID userId, String previousEmail, String newEmail, Instant occurredAt) {
}
