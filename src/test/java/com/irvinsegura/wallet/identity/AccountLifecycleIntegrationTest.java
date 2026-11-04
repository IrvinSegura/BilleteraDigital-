package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.RESET_PASSWORD_SUBJECT;
import static com.irvinsegura.wallet.support.ApiClient.VERIFY_EMAIL_SUBJECT;
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
    void verificationLinksWorkOnceAndOnlyTheNewestOne() throws Exception {
        String email = api.registerUnverified();
        String first = api.awaitLinkToken(email, VERIFY_EMAIL_SUBJECT, 1);

        api.register(email).andExpect(status().isAccepted());
        String second = api.awaitLinkToken(email, VERIFY_EMAIL_SUBJECT, 2);

        assertThat(second).isNotEqualTo(first);
        api.confirmEmail(first)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        api.confirmEmail(second).andExpect(status().isNoContent());
        api.confirmEmail(second).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        api.postJson(api.login(email, PASSWORD), "/api/v1/users/me/email-verification", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    void resettingAForgottenPasswordEndsEverySession() throws Exception {
        TestUser user = api.newUser();

        requestReset(user.email()).andExpect(status().isAccepted());
        resetPassword(api.awaitLinkToken(user.email(), RESET_PASSWORD_SUBJECT, 1), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        api.login(user.email(), NEW_PASSWORD);
    }

    @Test
    void resettingAForgottenPasswordLiftsTheLockoutAndVerifiesTheEmail() throws Exception {
        String email = api.registerUnverified();
        for (int i = 0; i < 5; i++) {
            login(email, "wrong password 123");
        }
        login(email, PASSWORD).andExpect(status().isLocked());

        requestReset(email).andExpect(status().isAccepted());
        String token = api.awaitLinkToken(email, RESET_PASSWORD_SUBJECT, 1);

        // A rejected password does not use up the link
        resetPassword(token, "short").andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        // Nor one built from the user's name, refused only after the link has been read
        resetPassword(token, "AnaLopez-2024!").andExpect(jsonPath("$.code").value("COMMON_PASSWORD"));
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        resetPassword(token, NEW_PASSWORD).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        // Following the link proved the address too, so the account can sign in
        TestUser signedIn = api.login(email, NEW_PASSWORD);
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
