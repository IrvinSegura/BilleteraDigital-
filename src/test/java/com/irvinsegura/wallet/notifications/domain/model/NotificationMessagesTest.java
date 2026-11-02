package com.irvinsegura.wallet.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationMessagesTest {

    private static final Instant AT = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void theSameEventAndRecipientAlwaysGiveTheSameId() {
        UUID depositId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Money amount = Money.ofMinor(10050, "MXN");

        Notification first = NotificationMessages.depositReceived(depositId, userId, amount, AT);
        Notification again = NotificationMessages.depositReceived(depositId, userId, amount, AT);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(first.body()).isEqualTo("100.50 MXN was added to your wallet.");
        assertThat(first.isRead()).isFalse();
    }

    @Test
    void bothSidesOfATransferGetTheirOwnNotification() {
        UUID transferId = UUID.randomUUID();
        Money amount = Money.ofMinor(3025, "USD");

        Notification sent = NotificationMessages.transferSent(transferId, UUID.randomUUID(), "bob@example.com",
                amount, AT);
        Notification received = NotificationMessages.transferReceived(transferId, UUID.randomUUID(), "Ana López",
                amount, AT);

        assertThat(sent.id()).isNotEqualTo(received.id());
        assertThat(sent.body()).isEqualTo("You sent 30.25 USD to bob@example.com.");
        assertThat(received.body()).isEqualTo("Ana López sent you 30.25 USD.");
        assertThat(received.referenceId()).isEqualTo(transferId);
    }

    @Test
    void lockoutSaysUntilWhenInUtc() {
        Notification locked = NotificationMessages.accountLocked(UUID.randomUUID(),
                Instant.parse("2026-10-30T12:15:30Z"), AT);

        assertThat(locked.body()).contains("until 2026-10-30 12:15 UTC");
        assertThat(locked.referenceId()).isNull();
    }

    @Test
    void accountEmailsCarryTheTokenInTheLinkFragment() {
        AccountEmails emails = new AccountEmails("https://wallet.example.com/");

        EmailMessage email = emails.resetPassword("ana@example.com", "Ana López", "abc_-123",
                Instant.parse("2026-10-30T12:30:00Z"));

        assertThat(email.subject()).isEqualTo("Reset your password");
        assertThat(email.body()).startsWith("Hi Ana López,")
                .contains("https://wallet.example.com/reset-password#token=abc_-123")
                .contains("expires at 2026-10-30 12:30 UTC");
    }

    @Test
    void passwordChangesAndAccountStatusNeverMentionInternalDetails() {
        UUID userId = UUID.randomUUID();

        assertThat(NotificationMessages.passwordChanged(userId, true, AT).title()).isEqualTo("Your password was reset");
        assertThat(NotificationMessages.passwordChanged(userId, false, AT).body()).contains("was changed");
        assertThat(NotificationMessages.accountDisabled(userId, AT).body()).contains("Contact support");
    }

    @Test
    void textMustFitTheColumns() {
        assertThatThrownBy(() -> Notification.create(NotificationType.WELCOME, "key", UUID.randomUUID(),
                "x".repeat(Notification.MAX_TITLE_LENGTH + 1), "body", null, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.create(NotificationType.WELCOME, "key", UUID.randomUUID(), "title",
                " ", null, AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emailsGreetTheUserByName() {
        Notification welcome = NotificationMessages.welcome(UUID.randomUUID(), AT);

        EmailMessage email = EmailMessage.of(welcome, "ana@example.com", "Ana López");

        assertThat(email.to()).isEqualTo("ana@example.com");
        assertThat(email.subject()).isEqualTo("Welcome to Digital Wallet");
        assertThat(email.body()).startsWith("Hi Ana López,").contains(welcome.body());
    }

    @Test
    void aRefusedDepositSaysTheMoneyIsComingBackWithoutSayingWhy() {
        Notification notification = NotificationMessages.depositRejected(UUID.randomUUID(), UUID.randomUUID(),
                Money.ofMinor(4000, "MXN"), AT);

        assertThat(notification.title()).isEqualTo("Deposit refunded");
        assertThat(notification.body()).contains("40.00 MXN", "refunded").doesNotContain("frozen", "disabled");
    }

    @Test
    void aReturnedWithdrawalSaysTheMoneyIsBackInTheWallet() {
        Notification notification = NotificationMessages.withdrawalFailed(UUID.randomUUID(), UUID.randomUUID(),
                Money.ofMinor(25000, "MXN"), AT);

        assertThat(notification.title()).isEqualTo("Withdrawal returned");
        assertThat(notification.body()).contains("250.00 MXN", "back in your wallet");
    }
}
