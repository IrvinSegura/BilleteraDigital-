package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/**
 * Emails with a link that proves the recipient owns the address. Unlike notifications they are never stored: the link
 * carries a secret, so it exists only in the email itself.
 * <p>
 * The token goes in the URL fragment ({@code #token=...}), which browsers never send to servers or in a
 * {@code Referer}, so it does not end up in the client app's access logs or third-party analytics.
 */
public final class AccountEmails {

    private final String appUrl;
    private final ZoneId zone;

    /**
     * @param appUrl base address of the client app that opens the links, e.g. {@code https://wallet.example.com}
     * @param zone   the zone the times in the emails are shown in
     */
    public AccountEmails(String appUrl, ZoneId zone) {
        Objects.requireNonNull(appUrl, "appUrl must not be null");
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public EmailMessage verifyEmail(String email, String fullName, Language language, String token,
            Instant expiresAt) {
        return withSecretLink(email, fullName, language, "verify-email", EmailDraft.Tone.INFO, "@",
                link("verify-email", token), expiresAt);
    }

    public EmailMessage resetPassword(String email, String fullName, Language language, String token,
            Instant expiresAt) {
        return withSecretLink(email, fullName, language, "reset-password", EmailDraft.Tone.WARNING, "✱",
                link("reset-password", token), expiresAt);
    }

    /**
     * Sent to the new address: following the link moves the account to it.
     */
    public EmailMessage changeEmail(String newEmail, String fullName, Language language, String token,
            Instant expiresAt) {
        return withSecretLink(newEmail, fullName, language, "change-email", EmailDraft.Tone.INFO, "@",
                link("confirm-email-change", token), expiresAt);
    }

    /**
     * Sent to the address the account no longer uses, so a takeover through a hijacked session does not go unnoticed.
     * No link: the old address can no longer sign in.
     */
    public EmailMessage emailChanged(String previousEmail, String newEmail, String fullName, Language language,
            Instant at) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = EmailDrafts.start(texts, "email-changed", EmailDraft.Tone.WARNING, "!",
                Map.of("name", fullName), Map.of("newEmail", newEmail));
        draft.detail(texts.get("label.date"), texts.dateTime(at));
        return EmailLayout.message(previousEmail, draft);
    }

    /**
     * The last email the account gets.
     */
    public EmailMessage accountClosed(String email, String fullName, Language language, Instant at) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = EmailDrafts.start(texts, "account-closed", EmailDraft.Tone.INFO, "✓",
                Map.of("name", fullName), Map.of());
        draft.detail(texts.get("label.date"), texts.dateTime(at));
        return EmailLayout.message(email, draft);
    }

    private EmailMessage withSecretLink(String email, String fullName, Language language, String reason,
            EmailDraft.Tone tone, String icon, String url, Instant expiresAt) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = EmailDrafts.start(texts, reason, tone, icon, Map.of("name", fullName), Map.of());
        draft.detail(texts.get("label.link-expires"), texts.dateTime(expiresAt));
        draft.detail(texts.get("label.link-uses"), texts.get("label.link-uses-once"));
        draft.action = new EmailDraft.Action(texts.get(reason + ".action"), url);
        draft.linkHint = texts.get("link-hint");
        return EmailLayout.message(email, draft);
    }

    /**
     * No link with a secret: it only points to pages the owner can open anyway.
     */
    public EmailMessage signUpAttempted(String email, String fullName, Language language) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = EmailDrafts.start(texts, "sign-up-attempted", EmailDraft.Tone.WARNING, "!",
                Map.of("name", fullName), Map.of());
        draft.action = new EmailDraft.Action(texts.get("sign-up-attempted.action"), appUrl + "/login");
        draft.secondaryAction = new EmailDraft.Action(texts.get("sign-up-attempted.secondary"),
                appUrl + "/forgot-password");
        return EmailLayout.message(email, draft);
    }

    /**
     * Sent once, when an administrator creates a staff account, and the only place its password appears.
     *
     * @param role {@code ADMIN} or {@code SUPPORT}
     */
    public EmailMessage staffCredentials(String email, String fullName, Language language, String role,
            String password) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = EmailDrafts.start(texts, "staff-credentials", EmailDraft.Tone.INFO, "→",
                Map.of("name", fullName), Map.of());
        draft.detail(texts.get("label.role"), texts.get("role." + role));
        draft.detail(texts.get("label.email"), email);
        draft.code(texts.get("label.password"), password);
        draft.action = new EmailDraft.Action(texts.get("staff-credentials.action"), appUrl + "/login");
        return EmailLayout.message(email, draft);
    }

    /**
     * Sent at each sign-in unless the user turned it off. No link with a secret: it only points to the session list,
     * where an unknown session can be ended.
     */
    public EmailMessage newSignIn(String email, String fullName, Language language, Instant at) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = EmailDrafts.start(texts, "new-sign-in", EmailDraft.Tone.INFO, "→",
                Map.of("name", fullName), Map.of());
        draft.detail(texts.get("label.date"), texts.dateTime(at));
        draft.action = new EmailDraft.Action(texts.get("new-sign-in.action"), appUrl + "/settings");
        draft.footerLink = new EmailDraft.Action(texts.get("footer.preferences"), appUrl + "/settings");
        return EmailLayout.message(email, draft);
    }

    private String link(String page, String token) {
        return appUrl + "/" + page + "#token=" + token;
    }
}
