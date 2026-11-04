package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone tried to sign up with the address of an existing, verified account. Registration answers the same as for a
 * new address, so only the owner learns about it, by email.
 */
public record ExistingAccountSignUpAttempted(UUID userId, Instant occurredAt) {
}
