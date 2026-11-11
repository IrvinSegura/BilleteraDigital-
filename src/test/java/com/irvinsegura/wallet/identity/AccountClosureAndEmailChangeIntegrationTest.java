package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.newClabe;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.Mailbox;
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
 * What owners do with the account itself: move it to another address, take a copy of their data, close it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccountClosureAndEmailChangeIntegrationTest {

    private static final String ME = "/api/v1/users/me";
    private static final String CHANGE_EMAIL_SUBJECT = "Confirm your new email address";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private Mailbox mailbox;
    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        mailbox = new Mailbox(mailpit);
        user = api.newUser();
    }

    @Test
    void theAccountMovesToTheNewAddressOnlyOnceTheLinkSentThereIsFollowed() throws Exception {
        String newEmail = "moved-" + UUID.randomUUID() + "@example.com";

        changeEmail(user, newEmail, "wrong password").andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        changeEmail(user, user.email(), PASSWORD).andExpect(status().isConflict());
        changeEmail(user, newEmail, PASSWORD).andExpect(status().isAccepted());

        // Nothing changes until the new address proves it is theirs
        String token = api.awaitLinkToken(newEmail, CHANGE_EMAIL_SUBJECT, 1);
        api.get(user, ME).andExpect(jsonPath("$.email").value(user.email()));

        confirm(token).andExpect(status().isNoContent());
        confirm(token).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        login(user.email()).andExpect(status().isUnauthorized());
        TestUser moved = api.login(newEmail, PASSWORD);
        api.get(moved, ME)
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertEventually(() -> assertThat(mailbox.subjectsFor(user.email())).contains("Your account's email changed"));
    }

    @Test
    void anAddressThatAlreadyHasAnAccountGetsNoLink() throws Exception {
        TestUser other = api.newUser();

        changeEmail(user, other.email(), PASSWORD).andExpect(status().isAccepted());
        changeEmail(user, "free-" + UUID.randomUUID() + "@example.com", PASSWORD).andExpect(status().isAccepted());

        // The second request's link arrives; the first never does, so the answer revealed nothing
        assertEventually(() -> assertThat(mailbox.subjectsFor(other.email())).doesNotContain(CHANGE_EMAIL_SUBJECT));
    }

    @Test
    void theExportHasEverythingButSecrets() throws Exception {
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "25.00").andExpect(status().isCreated());
        String clabe = newClabe();
        api.addBankAccount(user, clabe).andExpect(status().isCreated());

        String export = api.get(user, ME + "/export")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.profile.email").value(user.email()))
                .andExpect(jsonPath("$.sessions").isNotEmpty())
                .andExpect(jsonPath("$.wallets[0].balance").value("25.00"))
                .andExpect(jsonPath("$.wallets[0].movements[0].amount").value("25.00"))
                .andExpect(jsonPath("$.bankAccounts[0].clabeLastFour").value(clabe.substring(14)))
                .andExpect(content().string(not(containsString(clabe))))
                .andReturn().getResponse().getContentAsString();
        assertEventually(() -> assertThat((List<String>) JsonPath.read(
                api.get(user, ME + "/export").andReturn().getResponse().getContentAsString(),
                "$.notifications[*].type")).contains("DEPOSIT_RECEIVED"));
        assertThat(export).doesNotContain("passwordHash", "refreshToken");
    }

    @Test
    void anAccountClosesOnlyWithNothingLeftInItAndNeverSignsInAgain() throws Exception {
        String userId = read(api.get(user, ME), "$.id");
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "10.00").andExpect(status().isCreated());

        close(user, "wrong password").andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        close(user, PASSWORD)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.reason").value("BALANCE"));

        api.withdraw(user, walletId, "10.00");
        assertThat(api.balance(user, walletId)).isEqualTo("0.00");
        close(user, PASSWORD).andExpect(status().isNoContent());

        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        login(user.email())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CLOSED"));
        assertEventually(() -> assertThat(mailbox.subjectsFor(user.email())).contains("You closed your account"));

        // An administrator can disable or enable accounts, but a closed one stays closed
        TestUser admin = api.admin();
        api.get(admin, "/api/v1/admin/users/" + userId).andExpect(jsonPath("$.status").value("CLOSED"));
        api.postJson(admin, "/api/v1/admin/users/" + userId + "/enable", """
                {"reason": "Customer asked to come back"}""").andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void theAdministratorCannotCloseTheirAccount() throws Exception {
        close(api.admin(), "admin-test-password")
                .andExpect(jsonPath("$.code").value("ACCOUNT_CANNOT_BE_CLOSED"))
                .andExpect(jsonPath("$.reason").value("ADMIN"));
    }

    private ResultActions changeEmail(TestUser caller, String newEmail, String password) throws Exception {
        return api.postJson(caller, ME + "/email", """
                {"newEmail": "%s", "currentPassword": "%s"}""".formatted(newEmail, password));
    }

    private ResultActions close(TestUser caller, String password) throws Exception {
        return api.postJson(caller, ME + "/close", """
                {"currentPassword": "%s"}""".formatted(password));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}""".formatted(token)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                {"refreshToken": "%s"}""".formatted(refreshToken)));
    }
}
