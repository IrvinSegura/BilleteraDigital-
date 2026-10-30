package com.irvinsegura.wallet.identity;

import java.util.UUID;

/**
 * @param email normalized (lower case) email
 */
public record UserReference(UUID id, String email) {
}
