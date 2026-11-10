package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The email copy of each notification, designed for its reason and written in the recipient's language from the
 * figures stored with the notification.
 */
public final class NotificationEmails {

    // Notifications whose wording depends on their params
    private static final Set<NotificationType> NEED_PARAMS = EnumSet.complementOf(EnumSet.of(NotificationType.WELCOME,
            NotificationType.TWO_FACTOR_ENABLED, NotificationType.ACCOUNT_DISABLED, NotificationType.ACCOUNT_ENABLED,
            NotificationType.PHONE_CHANGED, NotificationType.ACCOUNT_UNLOCKED, NotificationType.SUPPORT_CASE_ASSIGNED,
            NotificationType.SUPPORT_AGENT_JOINED, NotificationType.SUPPORT_AGENT_REPLIED,
            NotificationType.SUPPORT_CASE_RESOLVED, NotificationType.IDENTITY_VERIFIED));

    private final String appUrl;
    private final ZoneId zone;

    /**
     * @param appUrl base address of the client app the emails link to
     * @param zone   the zone the times in the emails are shown in
     */
    public NotificationEmails(String appUrl, ZoneId zone) {
        Objects.requireNonNull(appUrl, "appUrl must not be null");
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public EmailMessage of(Notification notification, String email, String fullName, Language language) {
        EmailTexts texts = new EmailTexts(language, zone);
        EmailDraft draft = NEED_PARAMS.contains(notification.type()) && notification.params().isEmpty()
                ? storedText(notification, texts, fullName)
                : designed(notification, texts, fullName);
        // Only these can be switched off; security notices and failed operations always arrive
        if (EmailPreferences.isOptional(notification.type())) {
            draft.footerLink = new EmailDraft.Action(texts.get("footer.preferences"), appUrl + "/settings");
        }
        return EmailLayout.message(email, draft);
    }

    // Created before the figures were stored, so still in the outbox from then: all there is to send is its own text
    private EmailDraft storedText(Notification notification, EmailTexts texts, String fullName) {
        EmailDraft draft = new EmailDraft(texts.language(), EmailDraft.Tone.INFO, "i", texts.get("kicker.notification"),
                texts.get("brand"), notification.title(), notification.body(), notification.title(),
                texts.get("greeting", Map.of("name", fullName)), texts.get("sign-off"));
        draft.paragraphs.add(notification.body());
        draft.action = new EmailDraft.Action(texts.get("action.notifications"), appUrl + "/notifications");
        draft.footer.add(texts.get("footer.reason"));
        draft.footer.add(texts.get("footer.security"));
        return draft;
    }

    private EmailDraft designed(Notification notification, EmailTexts texts, String name) {
        Map<String, String> params = notification.params();
        return switch (notification.type()) {
            case WELCOME -> draft(notification, texts, name, "welcome", EmailDraft.Tone.SUCCESS, "✦", "/wallets");
            case ACCOUNT_LOCKED -> locked(notification, texts, name);
            case PASSWORD_CHANGED -> "reset".equals(params.get("variant"))
                    ? draft(notification, texts, name, "password-reset-done", EmailDraft.Tone.WARNING, "✱", "/settings")
                    : draft(notification, texts, name, "password-changed", EmailDraft.Tone.WARNING, "✱", "/settings");
            case TWO_FACTOR_ENABLED ->
                    draft(notification, texts, name, "two-factor-enabled", EmailDraft.Tone.SUCCESS, "✓", "/settings");
            case TWO_FACTOR_DISABLED -> "support".equals(params.get("variant"))
                    ? draft(notification, texts, name, "two-factor-reset", EmailDraft.Tone.DANGER, "!", "/settings")
                    : draft(notification, texts, name, "two-factor-disabled", EmailDraft.Tone.DANGER, "!", "/settings");
            case ACCOUNT_DISABLED -> draft(notification, texts, name, "account-disabled", EmailDraft.Tone.DANGER, "!", null);
            case ACCOUNT_ENABLED -> draft(notification, texts, name, "account-enabled", EmailDraft.Tone.SUCCESS, "✓", "/login");
            case DEPOSIT_RECEIVED ->
                    moved(notification, texts, name, "deposit-received", EmailDraft.Tone.SUCCESS, "↓", "/wallets");
            case DEPOSIT_REJECTED ->
                    moved(notification, texts, name, "deposit-rejected", EmailDraft.Tone.WARNING, "↺", "/wallets");
            case WITHDRAWAL_SENT ->
                    moved(notification, texts, name, "withdrawal-sent", EmailDraft.Tone.INFO, "↑", "/wallets");
            case WITHDRAWAL_FAILED ->
                    moved(notification, texts, name, "withdrawal-failed", EmailDraft.Tone.DANGER, "↺", "/bank-accounts");
            case TRANSFER_SENT -> {
                EmailDraft draft = moved(notification, texts, name, "transfer-sent", EmailDraft.Tone.INFO, "↑", "/wallets");
                draft.details.addFirst(new EmailDraft.Detail(texts.get("label.to"), params.get("recipient")));
                yield draft;
            }
            case TRANSFER_RECEIVED -> {
                EmailDraft draft = moved(notification, texts, name, "transfer-received", EmailDraft.Tone.SUCCESS, "↓", "/wallets");
                draft.details.addFirst(new EmailDraft.Detail(texts.get("label.from"), params.get("sender")));
                yield draft;
            }
            case WALLET_FROZEN ->
                    draft(notification, texts, name, "wallet-frozen", EmailDraft.Tone.DANGER, "‖", null)
                            .detail(texts.get("label.wallet"), params.get("currency"));
            case WALLET_UNFROZEN ->
                    draft(notification, texts, name, "wallet-unfrozen", EmailDraft.Tone.SUCCESS, "✓", "/wallets")
                            .detail(texts.get("label.wallet"), params.get("currency"));
            case BANK_ACCOUNT_ADDED ->
                    draft(notification, texts, name, "bank-account-added", EmailDraft.Tone.WARNING, "+", "/bank-accounts")
                            .code(texts.get("label.account"), "•••• " + params.get("lastFour"))
                            .detail(texts.get("label.date"), texts.dateTime(notification.createdAt()));
            case BANK_ACCOUNT_REMOVED ->
                    draft(notification, texts, name, "bank-account-removed", EmailDraft.Tone.WARNING, "−",
                            "/bank-accounts")
                            .code(texts.get("label.account"), "•••• " + params.get("lastFour"))
                            .detail(texts.get("label.date"), texts.dateTime(notification.createdAt()));
            case PHONE_CHANGED -> draft(notification, texts, name, "phone-changed", EmailDraft.Tone.WARNING, "#",
                    "/settings")
                    .detail(texts.get("label.date"), texts.dateTime(notification.createdAt()));
            case SUPPORT_CASE_ASSIGNED -> draft(notification, texts, name, "support-case-assigned", EmailDraft.Tone.INFO, "?",
                    "/support/inbox/" + notification.referenceId());
            case SUPPORT_AGENT_JOINED ->
                    draft(notification, texts, name, "support-agent-joined", EmailDraft.Tone.INFO, "+", "/support");
            case SUPPORT_AGENT_REPLIED ->
                    draft(notification, texts, name, "support-agent-replied", EmailDraft.Tone.INFO, "+", "/support");
            case SUPPORT_CASE_RESOLVED ->
                    draft(notification, texts, name, "support-case-resolved", EmailDraft.Tone.SUCCESS, "✓", "/support");
            case SUPPORT_TICKET_RESOLVED ->
                    draft(notification, texts, name, "support-ticket-resolved", EmailDraft.Tone.SUCCESS, "✓", "/support")
                            .detail(texts.get("label.resolution"), params.get("resolution"));
            case SUPPORT_ESCALATION_RESOLVED -> draft(notification, texts, name, "support-escalation-resolved",
                    EmailDraft.Tone.SUCCESS, "✓", "/support/escalated")
                    .detail(texts.get("label.adminResolution"), params.get("resolution"));
            case WALLET_ADJUSTED -> "DEBIT".equals(params.get("direction"))
                    ? moved(notification, texts, name, "wallet-debited", EmailDraft.Tone.WARNING, "−", "/wallets")
                            .detail(texts.get("label.reason"), params.get("reason"))
                    : moved(notification, texts, name, "wallet-credited", EmailDraft.Tone.SUCCESS, "+", "/wallets")
                            .detail(texts.get("label.reason"), params.get("reason"));
            case IDENTITY_VERIFIED ->
                    draft(notification, texts, name, "identity-verified", EmailDraft.Tone.SUCCESS, "✓",
                            "/bank-accounts");
            case IDENTITY_REJECTED ->
                    draft(notification, texts, name, "identity-rejected", EmailDraft.Tone.WARNING, "!",
                            "/settings/identity")
                            .detail(texts.get("label.reason"), params.get("reason"));
            case ACCOUNT_UNLOCKED ->
                    draft(notification, texts, name, "account-unlocked", EmailDraft.Tone.SUCCESS, "✓", "/login");
            case SESSIONS_REVOKED -> draft(notification, texts, name,
                    "1".equals(params.get("count")) ? "session-revoked" : "sessions-revoked",
                    EmailDraft.Tone.WARNING, "×", "/settings")
                    .detail(texts.get("label.sessions"), params.get("count"))
                    .detail(texts.get("label.date"), texts.dateTime(notification.createdAt()));
        };
    }

    private EmailDraft locked(Notification notification, EmailTexts texts, String name) {
        String until = texts.dateTime(Instant.parse(notification.params().get("until")));
        return draft(notification, texts, name, "account-locked", EmailDraft.Tone.DANGER, "×", "/forgot-password")
                .detail(texts.get("label.locked-until"), until);
    }

    // Money that moved: its amount stands out, followed by when it happened and the folio that finds it in the app
    private EmailDraft moved(Notification notification, EmailTexts texts, String name, String reason,
            EmailDraft.Tone tone, String icon, String path) {
        EmailDraft draft = draft(notification, texts, name, reason, tone, icon, path);
        draft.amountLabel = texts.get(reason + ".amount-label");
        draft.amount = amount(notification);
        draft.detail(texts.get("label.date"), texts.dateTime(notification.createdAt()));
        if (notification.referenceId() != null) {
            draft.code(texts.get("label.folio"), notification.referenceId().toString().substring(0, 8));
        }
        return draft;
    }

    private EmailDraft draft(Notification notification, EmailTexts texts, String name, String reason,
            EmailDraft.Tone tone, String icon, String path) {
        Map<String, String> params = notification.params();
        Map<String, String> values = new HashMap<>(params);
        if (params.containsKey("amount")) {
            values.put("amount", amount(notification));
        }
        EmailDraft draft = EmailDrafts.start(texts, reason, tone, icon, Map.of("name", name), values);
        if (path != null) {
            draft.action = new EmailDraft.Action(texts.get(reason + ".action"), appUrl + path);
        }
        return draft;
    }

    private static String amount(Notification notification) {
        return EmailTexts.money(notification.params().get("amount"), notification.params().get("currency"));
    }
}
