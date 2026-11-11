package com.irvinsegura.wallet.identity.application;

import java.util.UUID;

/**
 * What the support search previews of a user that matches what is being typed.
 */
public record UserSuggestion(UUID id, String email, String fullName, String status) {
}
