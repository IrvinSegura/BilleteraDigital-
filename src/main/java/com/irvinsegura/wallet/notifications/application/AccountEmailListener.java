package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.identity.AccountTokens;
import com.irvinsegura.wallet.identity.EmailVerificationRequested;
import com.irvinsegura.wallet.identity.PasswordResetRequested;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.notifications.domain.model.AccountEmails;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the emails with a verification or password reset link. The token is issued here, inside the listener's
 * transaction, instead of travelling in the event: the outbox never stores a usable secret, and if sending fails the
 * token is rolled back and the retry issues a new one. A retry after the user asked again just sends the newest link.
 */
@Component
class AccountEmailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailListener.class);

    private final AccountTokens accountTokens;
    private final UserDirectory users;
    private final AccountEmails accountEmails;
    private final EmailSender emailSender;

    AccountEmailListener(AccountTokens accountTokens, UserDirectory users, AccountEmails accountEmails,
            EmailSender emailSender) {
        this.accountTokens = accountTokens;
        this.users = users;
        this.accountEmails = accountEmails;
        this.emailSender = emailSender;
    }

    @ApplicationModuleListener
    void on(EmailVerificationRequested event) {
        Optional<UserContact> contact = users.findContact(event.userId());
        // Empty when the address was verified in the meantime, e.g. through a password reset
        contact.flatMap(found -> accountTokens.issueEmailVerification(found.id()))
                .ifPresentOrElse(
                        token -> emailSender.send(accountEmails.verifyEmail(contact.get().email(),
                                contact.get().fullName(), token.value(), token.expiresAt())),
                        () -> log.info("Verification email for user {} skipped: nothing to verify", event.userId()));
    }

    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        Optional<UserContact> contact = users.findContact(event.userId());
        // Empty when the account was disabled in the meantime
        contact.flatMap(found -> accountTokens.issuePasswordReset(found.id()))
                .ifPresentOrElse(
                        token -> emailSender.send(accountEmails.resetPassword(contact.get().email(),
                                contact.get().fullName(), token.value(), token.expiresAt())),
                        () -> log.info("Password reset email for user {} skipped: account not eligible",
                                event.userId()));
    }
}
