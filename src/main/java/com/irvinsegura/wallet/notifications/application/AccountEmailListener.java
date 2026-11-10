package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.identity.AccountClosed;
import com.irvinsegura.wallet.identity.AccountTokens;
import com.irvinsegura.wallet.identity.EmailChangeRequested;
import com.irvinsegura.wallet.identity.EmailChanged;
import com.irvinsegura.wallet.identity.EmailVerificationRequested;
import com.irvinsegura.wallet.identity.ExistingAccountSignUpAttempted;
import com.irvinsegura.wallet.identity.PasswordResetRequested;
import com.irvinsegura.wallet.identity.StaffAccountCreated;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserSignedIn;
import com.irvinsegura.wallet.notifications.domain.model.EmailPreferences;
import com.irvinsegura.wallet.notifications.domain.model.AccountEmails;
import com.irvinsegura.wallet.notifications.domain.port.EmailPreferencesRepository;
import com.irvinsegura.wallet.notifications.domain.port.EmailSender;
import com.irvinsegura.wallet.shared.domain.Language;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the emails about the account itself: a verification or password reset link, a notice that someone tried
 * to sign up with its address, or one that someone signed in. The token is issued here, inside the listener's
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
    private final EmailPreferencesRepository preferences;

    AccountEmailListener(AccountTokens accountTokens, UserDirectory users, AccountEmails accountEmails,
            EmailSender emailSender, EmailPreferencesRepository preferences) {
        this.accountTokens = accountTokens;
        this.users = users;
        this.accountEmails = accountEmails;
        this.emailSender = emailSender;
        this.preferences = preferences;
    }

    @ApplicationModuleListener
    void on(EmailVerificationRequested event) {
        Optional<UserContact> contact = users.findContact(event.userId());
        // Empty when the address was verified in the meantime, e.g. through a password reset
        contact.flatMap(found -> accountTokens.issueEmailVerification(found.id()))
                .ifPresentOrElse(
                        token -> emailSender.send(accountEmails.verifyEmail(contact.get().email(),
                                contact.get().fullName(), language(event.language(), contact.get()), token.value(),
                                token.expiresAt())),
                        () -> log.info("Verification email for user {} skipped: nothing to verify", event.userId()));
    }

    @ApplicationModuleListener
    void on(ExistingAccountSignUpAttempted event) {
        users.findContact(event.userId()).ifPresent(contact ->
                emailSender.send(accountEmails.signUpAttempted(contact.email(), contact.fullName(),
                        contact.language())));
    }

    /**
     * The password is generated here for the same reason as the tokens: it is never in the outbox, and if sending fails
     * it is rolled back and the retry generates another one.
     */
    @ApplicationModuleListener
    void on(StaffAccountCreated event) {
        Optional<UserContact> contact = users.findContact(event.userId());
        contact.flatMap(found -> accountTokens.issueInitialPassword(found.id()))
                .ifPresentOrElse(
                        password -> emailSender.send(accountEmails.staffCredentials(contact.get().email(),
                                contact.get().fullName(), contact.get().language(), event.role(), password)),
                        () -> log.info("Account data email for user {} skipped: account not eligible", event.userId()));
    }

    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        Optional<UserContact> contact = users.findContact(event.userId());
        // Empty when the account was disabled in the meantime
        contact.flatMap(found -> accountTokens.issuePasswordReset(found.id()))
                .ifPresentOrElse(
                        token -> emailSender.send(accountEmails.resetPassword(contact.get().email(),
                                contact.get().fullName(), language(event.language(), contact.get()), token.value(),
                                token.expiresAt())),
                        () -> log.info("Password reset email for user {} skipped: account not eligible",
                                event.userId()));
    }

    @ApplicationModuleListener
    void on(EmailChangeRequested event) {
        Optional<UserContact> contact = users.findContact(event.userId());
        // Empty when the address got an account in the meantime, or this one was closed or disabled
        contact.flatMap(found -> accountTokens.issueEmailChange(found.id(), event.newEmail()))
                .ifPresentOrElse(
                        token -> emailSender.send(accountEmails.changeEmail(event.newEmail(),
                                contact.get().fullName(), language(event.language(), contact.get()), token.value(),
                                token.expiresAt())),
                        () -> log.info("Email change link for user {} skipped: not eligible", event.userId()));
    }

    @ApplicationModuleListener
    void on(EmailChanged event) {
        users.findContact(event.userId()).ifPresent(contact ->
                emailSender.send(accountEmails.emailChanged(event.previousEmail(), event.newEmail(),
                        contact.fullName(), contact.language(), event.occurredAt())));
    }

    @ApplicationModuleListener
    void on(AccountClosed event) {
        users.findContact(event.userId()).ifPresent(contact ->
                emailSender.send(accountEmails.accountClosed(contact.email(), contact.fullName(), contact.language(),
                        event.occurredAt())));
    }

    @ApplicationModuleListener
    void on(UserSignedIn event) {
        if (!preferences.find(event.userId()).orElse(EmailPreferences.DEFAULT).signIns()) {
            return;
        }
        users.findContact(event.userId()).ifPresent(contact ->
                emailSender.send(accountEmails.newSignIn(contact.email(), contact.fullName(), contact.language(),
                        event.occurredAt())));
    }

    // The language of the app that asked, if it said; events stored before there was one don't have it either
    private static Language language(Language requested, UserContact contact) {
        return requested != null ? requested : contact.language();
    }
}
