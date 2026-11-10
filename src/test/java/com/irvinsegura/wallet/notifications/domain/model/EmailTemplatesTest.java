package com.irvinsegura.wallet.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.shared.domain.Money;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmailTemplatesTest {

    private static final Instant AT = Instant.parse("2026-11-04T18:30:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final String APP = "https://wallet.example.com";
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OPERATION = UUID.fromString("3f2a91bc-1111-4222-8333-444455556666");
    private static final Money AMOUNT = Money.ofMinor(125050, "MXN");
    private static final Currency MXN = Currency.getInstance("MXN");

    private final AccountEmails accountEmails = new AccountEmails(APP + "/", ZONE);
    private final NotificationEmails notificationEmails = new NotificationEmails(APP, ZONE);

    private static Map<String, Notification> everyNotification() {
        Map<String, Notification> all = new LinkedHashMap<>();
        all.put("welcome", NotificationMessages.welcome(USER, AT));
        all.put("account-locked", NotificationMessages.accountLocked(USER, AT.plusSeconds(900), AT));
        all.put("password-changed", NotificationMessages.passwordChanged(USER, false, AT));
        all.put("password-reset-done", NotificationMessages.passwordChanged(USER, true, AT));
        all.put("two-factor-enabled", NotificationMessages.twoFactorEnabled(USER, AT));
        all.put("two-factor-disabled", NotificationMessages.twoFactorDisabled(USER, false, AT));
        all.put("two-factor-reset", NotificationMessages.twoFactorDisabled(USER, true, AT));
        all.put("account-disabled", NotificationMessages.accountDisabled(USER, AT));
        all.put("account-enabled", NotificationMessages.accountEnabled(USER, AT));
        all.put("deposit-received", NotificationMessages.depositReceived(OPERATION, USER, AMOUNT, AT));
        all.put("deposit-rejected", NotificationMessages.depositRejected(OPERATION, USER, AMOUNT, AT));
        all.put("withdrawal-sent", NotificationMessages.withdrawalSent(OPERATION, USER, AMOUNT, AT));
        all.put("withdrawal-failed", NotificationMessages.withdrawalFailed(OPERATION, USER, AMOUNT, AT));
        all.put("transfer-sent", NotificationMessages.transferSent(OPERATION, USER, "bob@example.com", AMOUNT, AT));
        all.put("transfer-received", NotificationMessages.transferReceived(OPERATION, USER, "Ana López", AMOUNT, AT));
        all.put("wallet-frozen", NotificationMessages.walletFrozen(OPERATION, USER, MXN, AT));
        all.put("wallet-unfrozen", NotificationMessages.walletUnfrozen(OPERATION, USER, MXN, AT));
        all.put("bank-account-added", NotificationMessages.bankAccountAdded(OPERATION, USER, "1234", AT));
        all.put("bank-account-removed", NotificationMessages.bankAccountRemoved(OPERATION, USER, "1234", AT));
        all.put("phone-changed", NotificationMessages.phoneChanged(USER, AT));
        all.put("account-unlocked", NotificationMessages.accountUnlocked(USER, AT));
        all.put("support-case-assigned", NotificationMessages.supportCaseAssigned(OPERATION, USER, AT));
        all.put("support-agent-joined", NotificationMessages.supportAgentJoined(OPERATION, USER, AT));
        all.put("support-agent-replied", NotificationMessages.supportAgentReplied(OPERATION, USER, AT));
        all.put("support-case-resolved", NotificationMessages.supportCaseResolved(OPERATION, USER, AT));
        all.put("support-ticket-resolved", NotificationMessages.supportTicketResolved(OPERATION, USER,
                "We confirmed the payment with the provider and credited the deposit.", AT));
        all.put("support-escalation-resolved", NotificationMessages.supportEscalationResolved(OPERATION, USER,
                "We confirmed the payment with the provider and credited the deposit.", AT));
        all.put("wallet-credited", NotificationMessages.walletAdjusted(OPERATION, USER, true, AMOUNT,
                "The deposit of November 3 was never credited.", AT));
        all.put("wallet-debited", NotificationMessages.walletAdjusted(OPERATION, USER, false, AMOUNT,
                "The deposit was credited twice.", AT));
        all.put("identity-verified", NotificationMessages.identityVerified(OPERATION, USER, AT));
        all.put("identity-rejected", NotificationMessages.identityRejected(OPERATION, USER,
                "The front photo is blurry.", AT));
        all.put("session-revoked", NotificationMessages.sessionsRevoked(USER, 1, AT));
        all.put("sessions-revoked", NotificationMessages.sessionsRevoked(USER, 3, AT));
        return all;
    }

    private static Properties bundle(Language language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = EmailTemplatesTest.class.getResourceAsStream("emails_" + language.tag() + ".properties")) {
            properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void bothLanguagesHaveTheSameTexts() throws IOException {
        assertThat(bundle(Language.ES).stringPropertyNames()).isEqualTo(bundle(Language.EN).stringPropertyNames());
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyNotificationHasItsOwnEmailWithNoValueLeftUnfilled(Language language) {
        everyNotification().forEach((reason, notification) -> {
            EmailMessage email = notificationEmails.of(notification, "ana@example.com", "Ana López", language);

            assertThat(email.subject()).as(reason).isNotBlank().doesNotContain("{");
            assertThat(email.body()).as(reason).doesNotContain("{", "null");
            assertThat(email.html()).as(reason)
                    .contains("<html lang=\"" + language.tag() + "\"")
                    .doesNotContain("null");
        });
    }

    // Emoji look different in every client and some show them as empty boxes, so icons are typographic marks
    @ParameterizedTest
    @EnumSource(Language.class)
    void everyEmailIsSignedLabeledAndFreeOfEmoji(Language language) {
        String signOff = language == Language.ES ? "El equipo de Billetera Digital" : "The Digital Wallet team";
        everyEmail(language).forEach((reason, email) -> {
            assertThat(email.html()).as(reason).contains(signOff);
            assertThat(email.body()).as(reason).contains(signOff);
            assertThat(email.html().codePoints().filter(EmailTemplatesTest::isEmoji)).as(reason).isEmpty();
        });
    }

    @Test
    void everyNotificationTypeHasAnEmailInTheseTests() {
        assertThat(everyNotification().values().stream().map(Notification::type).distinct())
                .containsExactlyInAnyOrder(NotificationType.values());
    }

    @Test
    void onlyMoneyNoticesAndSignInsCanBeTurnedOff() {
        EmailPreferences none = new EmailPreferences(false, false, false);

        assertThat(none.allowsEmail(NotificationType.DEPOSIT_RECEIVED)).isFalse();
        assertThat(none.allowsEmail(NotificationType.PHONE_CHANGED)).isTrue();
        assertThat(none.allowsEmail(NotificationType.SESSIONS_REVOKED)).isTrue();
        assertThat(EmailPreferences.DEFAULT.signIns()).isTrue();
    }

    @Test
    void theStaffEmailHasTheRoleTheAddressAndThePassword() {
        EmailMessage email = accountEmails.staffCredentials("ana@example.com", "Ana López", Language.ES, "ADMIN",
                "abcd-EFGH-2345-jkmn");

        assertThat(email.subject()).isEqualTo("Tus datos de acceso a Billetera Digital").doesNotContain("abcd");
        assertThat(email.body()).contains("Rol: Administrador", "Correo: ana@example.com",
                "Contraseña: abcd-EFGH-2345-jkmn", APP + "/login");
        assertThat(email.html()).contains("monospace;letter-spacing:0.6px;", ">abcd-EFGH-2345-jkmn<");
    }

    @Test
    void theSignInEmailSaysWhenAndLinksToTheSessions() {
        EmailMessage email = accountEmails.newSignIn("ana@example.com", "Ana López", Language.ES, AT);

        assertThat(email.subject()).isEqualTo("Nuevo inicio de sesión en tu cuenta");
        assertThat(email.body()).contains("4 de noviembre de 2026", APP + "/settings");
    }

    @Test
    void theAmountOpensTheEmailAndTheFolioIsSetAsAReference() {
        EmailMessage email = notificationEmails.of(NotificationMessages.depositReceived(OPERATION, USER, AMOUNT, AT),
                "ana@example.com", "Ana López", Language.ES);

        assertThat(email.html().indexOf("$1,250.50 MXN")).isLessThan(email.html().indexOf("Hola Ana López"));
        assertThat(email.body()).startsWith("Monto depositado: $1,250.50 MXN");
        assertThat(email.html()).contains(">Depósito<", "monospace;letter-spacing:0.6px;", ">3f2a91bc<");
    }

    @Test
    void theEmailIsWrittenInTheRecipientsLanguage() {
        Notification deposit = NotificationMessages.depositReceived(OPERATION, USER, AMOUNT, AT);

        EmailMessage spanish = notificationEmails.of(deposit, "ana@example.com", "Ana López", Language.ES);
        EmailMessage english = notificationEmails.of(deposit, "ana@example.com", "Ana López", Language.EN);

        assertThat(spanish.subject()).isEqualTo("Recibimos tu depósito");
        assertThat(spanish.body()).contains("Hola Ana López,").contains("$1,250.50 MXN", "3f2a91bc", "de noviembre de 2026");
        assertThat(english.subject()).isEqualTo("Deposit received");
        assertThat(english.body()).contains("Hi Ana López,").contains("$1,250.50 MXN", "3f2a91bc", "November 4, 2026");
    }

    @Test
    void whatUsersWroteCannotAddMarkupToTheEmail() {
        Notification received = NotificationMessages.transferReceived(OPERATION, USER,
                "<a href=\"https://evil.example\">Soporte</a>", AMOUNT, AT);

        EmailMessage email = notificationEmails.of(received, "ana@example.com", "<img src=x onerror=alert(1)>",
                Language.ES);

        assertThat(email.html()).doesNotContain("<a href=\"https://evil.example\"", "<img src=x")
                .contains("&lt;a href=", "&lt;img src=x");
    }

    @Test
    void secretLinksReachBothFormsOfTheEmail() {
        EmailMessage email = accountEmails.verifyEmail("ana@example.com", "Ana López", Language.EN, "abc_-123", AT);

        assertThat(email.subject()).isEqualTo("Confirm your email address");
        assertThat(email.body()).contains("https://wallet.example.com/verify-email#token=abc_-123");
        assertThat(email.html()).contains("href=\"https://wallet.example.com/verify-email#token=abc_-123\"");
    }

    @Test
    void aNotificationFromBeforeTheFiguresWereStoredStillSaysWhatHappened() {
        Notification old = new Notification(UUID.randomUUID(), USER, NotificationType.DEPOSIT_RECEIVED,
                "Deposit received", "100.50 MXN was added to your wallet.", OPERATION, AT, null, Map.of());

        EmailMessage email = notificationEmails.of(old, "ana@example.com", "Ana López", Language.ES);

        assertThat(email.subject()).isEqualTo("Deposit received");
        assertThat(email.body()).startsWith("Hola Ana López,").contains("100.50 MXN was added to your wallet.");
    }

    @Test
    void onlyOptionalNoticesLinkToTheirSettings() {
        assertThat(notificationEmails.of(NotificationMessages.depositReceived(OPERATION, USER, AMOUNT, AT),
                "ana@example.com", "Ana", Language.ES).body()).contains(APP + "/settings");
        assertThat(notificationEmails.of(NotificationMessages.accountDisabled(USER, AT),
                "ana@example.com", "Ana", Language.ES).body()).doesNotContain(APP + "/settings");
    }

    private Map<String, EmailMessage> everyEmail(Language language) {
        Map<String, EmailMessage> emails = new LinkedHashMap<>();
        emails.put("verify-email", accountEmails.verifyEmail("ana@example.com", "Ana López", language, "token", AT));
        emails.put("reset-password", accountEmails.resetPassword("ana@example.com", "Ana López", language, "token", AT));
        emails.put("sign-up-attempted", accountEmails.signUpAttempted("ana@example.com", "Ana López", language));
        emails.put("staff-credentials", accountEmails.staffCredentials("ana@example.com", "Ana López", language,
                "SUPPORT", "abcd-EFGH-2345-jkmn"));
        emails.put("new-sign-in", accountEmails.newSignIn("ana@example.com", "Ana López", language, AT));
        emails.put("change-email", accountEmails.changeEmail("ana@new.example.com", "Ana López", language, "token", AT));
        emails.put("email-changed", accountEmails.emailChanged("ana@example.com", "ana@new.example.com", "Ana López",
                language, AT));
        emails.put("account-closed", accountEmails.accountClosed("ana@example.com", "Ana López", language, AT));
        everyNotification().forEach((reason, notification) ->
                emails.put(reason, notificationEmails.of(notification, "ana@example.com", "Ana López", language)));
        return emails;
    }

    private static boolean isEmoji(int codePoint) {
        return codePoint >= 0x1F000 || codePoint == 0xFE0F
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS
                || codePoint == 0x2714 || codePoint == 0x2709 || codePoint == 0x2744;
    }

    // Not an assertion: leaves every design in target/email-previews, with index.html showing them all side by side at
    // desktop and phone width, to open in a browser while working on them
    @Test
    void writesPreviews() throws IOException {
        Path folder = Path.of("target", "email-previews");
        Files.createDirectories(folder);
        StringBuilder gallery = new StringBuilder("""
                <!doctype html>
                <html lang="es"><head><meta charset="utf-8"><title>Billetera Digital: correos</title>
                <style>
                body{margin:0;padding:32px;background:#e5e7eb;font:14px -apple-system,'Segoe UI',Roboto,sans-serif;color:#0f172a}
                h2{margin:40px 0 12px;font-size:16px}
                .row{display:flex;gap:24px;align-items:flex-start;flex-wrap:wrap}
                iframe{border:1px solid #cbd5e1;background:#fff;border-radius:8px}
                </style></head><body><h1>Billetera Digital: correos</h1>
                """);
        for (Language language : Language.values()) {
            for (Map.Entry<String, EmailMessage> email : everyEmail(language).entrySet()) {
                String file = email.getKey() + "." + language.tag() + ".html";
                Files.writeString(folder.resolve(file), email.getValue().html());
                gallery.append("""
                        <h2>%s · %s</h2><div class="row">
                        <iframe src="%s" width="680" height="1100" title="%s"></iframe>
                        <iframe src="%s" width="375" height="1100" title="%s"></iframe></div>
                        """.formatted(email.getValue().subject().replace("<", "&lt;"), language.tag(), file, file, file,
                        file));
            }
        }
        Files.writeString(folder.resolve("index.html"), gallery.append("</body></html>\n").toString());
    }
}
