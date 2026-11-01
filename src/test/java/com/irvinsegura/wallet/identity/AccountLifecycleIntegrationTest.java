package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.RESET_PASSWORD_SUBJECT;
import static com.irvinsegura.wallet.support.ApiClient.VERIFY_EMAIL_SUBJECT;
import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every link is read from the email Mailpit received, as a user would follow it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccountLifecycleIntegrationTest {

    private static final String NEW_PASSWORD = "a brand new passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, mailpit);
    }

    @Test
    void unverifiedAccountsCanAddAndReceiveMoneyButNotTakeItOut() throws Exception {
        TestUser newcomer = api.newUnverifiedUser();
        TestUser friend = api.newUser();
        api.get(newcomer, "/api/v1/users/me").andExpect(jsonPath("$.emailVerified").value(false));
        String newcomerWallet = api.openWalletId(newcomer, "MXN");
        String friendWallet = api.openWalletId(friend, "MXN");
        api.deposit(friend, friendWallet, "50.00").andExpect(status().isCreated());

        api.deposit(newcomer, newcomerWallet, "100.00").andExpect(status().isCreated());
        api.transfer(friend, friendWallet, newcomer.email(), "10.00", newKey()).andExpect(status().isCreated());
        api.withdraw(newcomer, newcomerWallet, "1.00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        api.transfer(newcomer, newcomerWallet, friend.email(), "1.00", newKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(api.balance(newcomer, newcomerWallet)).isEqualTo("110.00");

        api.verifyEmail(newcomer);

        api.get(newcomer, "/api/v1/users/me").andExpect(jsonPath("$.emailVerified").value(true));
        api.withdraw(newcomer, newcomerWallet, "1.00").andExpect(status().isCreated());
    }

    @Test
    void verificationLinksWorkOnceAndOnlyTheNewestOne() throws Exception {
        TestUser user = api.newUnverifiedUser();
        String first = api.awaitLinkToken(user.email(), VERIFY_EMAIL_SUBJECT, 1);

        api.postJson(user, "/api/v1/users/me/email-verification", "").andExpect(status().isAccepted());
        String second = api.awaitLinkToken(user.email(), VERIFY_EMAIL_SUBJECT, 2);

        assertThat(second).isNotEqualTo(first);
        api.confirmEmail(first)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        api.confirmEmail(second).andExpect(status().isNoContent());
        api.confirmEmail(second).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        api.postJson(user, "/api/v1/users/me/email-verification", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void resettingAForgottenPasswordEndsSessionsAndLiftsTheLockout() throws Exception {
        TestUser user = api.newUnverifiedUser();
        for (int i = 0; i < 5; i++) {
            login(user.email(), "wrong password 123");
        }
        login(user.email(), PASSWORD).andExpect(status().isLocked());

        requestReset(user.email()).andExpect(status().isAccepted());
        String token = api.awaitLinkToken(user.email(), RESET_PASSWORD_SUBJECT, 1);

        // A rejected password does not use up the link
        resetPassword(token, "short").andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        resetPassword(token, NEW_PASSWORD).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        login(user.email(), PASSWORD).andExpect(status().isUnauthorized());
        TestUser signedIn = api.login(user.email(), NEW_PASSWORD);
        // Following the link proved the address too
        api.get(signedIn, "/api/v1/users/me").andExpect(jsonPath("$.emailVerified").value(true));
        assertEventually(() -> assertThat(notificationTitles(signedIn)).contains("Your password was reset"));
    }

    @Test
    void resetRequestsAnswerTheSameForUnknownEmails() throws Exception {
        TestUser user = api.newUser();
        String stranger = "nobody-" + UUID.randomUUID() + "@example.com";

        requestReset(stranger).andExpect(status().isAccepted());
        requestReset("not an email").andExpect(status().isAccepted());
        requestReset(user.email()).andExpect(status().isAccepted());

        // Nothing is published for an unknown address, so no email for it can still be on its way
        api.awaitLinkToken(user.email(), RESET_PASSWORD_SUBJECT, 1);
        assertThat(api.mailbox().subjectsFor(stranger)).isEmpty();
    }

    @Test
    void changingThePasswordEndsEveryOtherSession() throws Exception {
        TestUser phone = api.newUser();
        TestUser laptop = api.login(phone.email(), PASSWORD);

        changePassword(phone, "wrong password 123", NEW_PASSWORD)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        String newRefreshToken = read(changePassword(phone, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()), "$.refreshToken");

        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());
        refresh(phone.refreshToken()).andExpect(status().isUnauthorized());
        refresh(newRefreshToken).andExpect(status().isOk());
        login(phone.email(), PASSWORD).andExpect(status().isUnauthorized());
        login(phone.email(), NEW_PASSWORD).andExpect(status().isOk());
        assertEventually(() -> assertThat(api.mailbox().subjectsFor(phone.email()))
                .contains("Your password was changed"));
    }

    @Test
    void guessingTheCurrentPasswordLocksTheAccount() throws Exception {
        TestUser user = api.newUser();

        for (int i = 0; i < 5; i++) {
            changePassword(user, "wrong password 123", NEW_PASSWORD)
                    .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        }

        changePassword(user, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        login(user.email(), PASSWORD).andExpect(status().isLocked());
    }

    private ResultActions changePassword(TestUser user, String currentPassword, String newPassword)
            throws Exception {
        return api.postJson(user, "/api/v1/users/me/password", """
                {"currentPassword": "%s", "newPassword": "%s"}""".formatted(currentPassword, newPassword));
    }

    private ResultActions requestReset(String email) throws Exception {
        return postPublic("/api/v1/auth/password-reset", """
                {"email": "%s"}""".formatted(email));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return postPublic("/api/v1/auth/password-reset/confirm", """
                {"token": "%s", "newPassword": "%s"}""".formatted(token, newPassword));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postPublic("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}""".formatted(email, password));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postPublic("/api/v1/auth/refresh", """
                {"refreshToken": "%s"}""".formatted(refreshToken));
    }

    private ResultActions postPublic(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private List<String> notificationTitles(TestUser user) throws Exception {
        return JsonPath.read(api.get(user, "/api/v1/notifications").andReturn().getResponse().getContentAsString(),
                "$.items[*].title");
    }
}
