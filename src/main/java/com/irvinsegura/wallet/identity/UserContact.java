package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.Language;
import java.util.UUID;

/**
 * How to address a user in a message. Personal data: read it when the message is sent, never copy it into events
 * or logs.
 *
 * @param email    normalized (lower case) email
 * @param language the language to write to them in
 */
public record UserContact(UUID id, String email, String fullName, Language language) {
}
