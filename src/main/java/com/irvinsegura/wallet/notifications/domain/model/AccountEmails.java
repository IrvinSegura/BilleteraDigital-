package com.irvinsegura.wallet.notifications.domain.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Emails with a link that proves the recipient owns the address. Unlike notifications they are never stored: the link
 * carries a secret, so it exists only in the email itself.
 * <p>
 * The token goes in the URL fragment ({@code #token=...}), which browsers never send to servers or in a
 * {@code Referer}, so it does not end up in the client app's access logs or third-party analytics.
 */
public final class AccountEmails {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final String appUrl;

    /**
     * @param appUrl base address of the client app that opens the links, e.g. {@code https://wallet.example.com}
     */
    public AccountEmails(String appUrl) {
        Objects.requireNonNull(appUrl, "appUrl must not be null");
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    public EmailMessage verifyEmail(String email, String fullName, String token, Instant expiresAt) {
        return new EmailMessage(email, "Confirm your email address", """
                Hi %s,

                Confirm your email address to start sending and withdrawing money:

                %s

                The link works once and expires at %s. If you did not create a Digital Wallet account, you can \
                ignore this email.
                """.formatted(fullName, link("verify-email", token), TIME.format(expiresAt)));
    }

    public EmailMessage resetPassword(String email, String fullName, String token, Instant expiresAt) {
        return new EmailMessage(email, "Reset your password", """
                Hi %s,

                Someone asked to reset your Digital Wallet password. Choose a new one here:

                %s

                The link works once and expires at %s. Resetting also signs out every device and unblocks your \
                account if it was locked. If it was not you, ignore this email: your password stays the same.
                """.formatted(fullName, link("reset-password", token), TIME.format(expiresAt)));
    }

    /**
     * No link with a secret: it only points to pages the owner can open anyway.
     */
    public EmailMessage signUpAttempted(String email, String fullName) {
        return new EmailMessage(email, "Someone tried to sign up with your email", """
                Hi %s,

                Someone tried to create a Digital Wallet account with this email address, which already has one. \
                Nothing was changed and no new account was created.

                If it was you, sign in here: %s
                Forgot your password? Reset it here: %s

                If it was not you, you can ignore this email.
                """.formatted(fullName, appUrl + "/login", appUrl + "/forgot-password"));
    }

    private String link(String page, String token) {
        return appUrl + "/" + page + "#token=" + token;
    }
}
