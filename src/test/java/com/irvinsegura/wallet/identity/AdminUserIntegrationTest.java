package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminUserIntegrationTest {

    private static final String USERS = "/api/v1/admin/users/";
    private static final String REASON = """
            {"reason": "Customer reported a stolen phone"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private TestUser admin;
    private TestUser user;
    private String userId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
        user = api.newUser();
        userId = read(api.get(user, "/api/v1/users/me"), "$.id");
    }

    @Test
    void regularUsersCannotManageAccounts() throws Exception {
        TestUser other = api.newUser();

        search(other, user.email()).andExpect(status().isForbidden());
        api.postJson(other, USERS + userId + "/disable", REASON).andExpect(status().isForbidden());
        api.get(other, "/api/v1/admin/transfers/" + UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void supportFindsAUserByEmailWithTheirWallets() throws Exception {
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "100.00").andExpect(status().isCreated());

        search(admin, user.email().toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.locked").value(false));
        api.get(admin, USERS + userId + "/wallets")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(walletId))
                .andExpect(jsonPath("$[0].balance.amount").value("100.00"));
        search(admin, "nobody-" + UUID.randomUUID() + "@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void anAdministratorLiftsALockoutBeforeItExpires() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(user.email(), "wrong password 123");
        }
        api.get(admin, USERS + userId).andExpect(jsonPath("$.locked").value(true));

        api.postJson(admin, USERS + userId + "/unlock", """
                {"reason": " "}""").andExpect(status().isBadRequest());
        api.postJson(admin, USERS + userId + "/unlock", """
                {"reason": "Confirmed by phone"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        login(user.email(), PASSWORD).andExpect(status().isOk());
        assertEventually(() -> assertThat(auditActionsFor(userId)).contains("USER_UNLOCKED"));
        assertThat(auditFor(userId)).contains("reason: Confirmed by phone");
    }

    @Test
    void disablingAnAccountStopsLoginAndMoneyRightAway() throws Exception {
        TestUser friend = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        String friendWallet = api.openWalletId(friend, "MXN");
        api.deposit(user, walletId, "100.00").andExpect(status().isCreated());
        api.deposit(friend, friendWallet, "100.00").andExpect(status().isCreated());

        api.postJson(admin, USERS + userId + "/disable", REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        // The access token issued before still reads, but cannot move money
        api.get(user, "/api/v1/wallets/" + walletId).andExpect(status().isOk());
        api.deposit(user, walletId, "1.00").andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        api.withdraw(user, walletId, "1.00").andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        api.transfer(user, walletId, friend.email(), "1.00", newKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        login(user.email(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        // Money can still arrive; nothing can leave
        api.transfer(friend, friendWallet, user.email(), "5.00", newKey()).andExpect(status().isCreated());
        assertThat(api.balance(user, walletId)).isEqualTo("105.00");

        assertEventually(() -> assertThat(auditActionsFor(userId)).first().isEqualTo("USER_DISABLED"));
        assertThat((String) JsonPath.read(auditFor(userId), "$.items[0].details"))
                .contains("Customer reported a stolen phone");
        assertEventually(() -> assertThat(notificationTypes(user)).contains("ACCOUNT_DISABLED"));

        api.postJson(admin, USERS + userId + "/enable", REASON).andExpect(jsonPath("$.status").value("ACTIVE"));
        TestUser back = api.login(user.email(), PASSWORD);
        api.withdraw(back, walletId, "1.00").andExpect(status().isCreated());
    }

    @Test
    void aReasonIsRequiredAndTheAdministratorCannotBeDisabled() throws Exception {
        api.postJson(admin, USERS + userId + "/disable", "{}").andExpect(status().isBadRequest());

        String adminId = read(api.get(admin, "/api/v1/users/me"), "$.id");
        api.postJson(admin, USERS + adminId + "/disable", REASON)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ADMIN_CANNOT_BE_DISABLED"));
    }

    @Test
    void supportSeesAnyOperationById() throws Exception {
        TestUser friend = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.openWalletId(friend, "MXN");
        String depositId = read(api.deposit(user, walletId, "100.00"), "$.id");
        String withdrawalId = read(api.withdraw(user, walletId, "10.00"), "$.id");
        String transferId = read(api.transfer(user, walletId, friend.email(), "5.00", newKey()), "$.id");

        api.get(admin, "/api/v1/admin/deposits/" + depositId)
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.amount.amount").value("100.00"))
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty());
        api.get(admin, "/api/v1/admin/withdrawals/" + withdrawalId)
                .andExpect(jsonPath("$.balanceAfter.amount").value("90.00"));
        api.get(admin, "/api/v1/admin/transfers/" + transferId)
                .andExpect(jsonPath("$.senderId").value(userId))
                .andExpect(jsonPath("$.recipientEmail").value(friend.email()));
        api.get(admin, "/api/v1/admin/deposits/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_FOUND"));
        api.get(admin, "/api/v1/admin/withdrawals/" + transferId)
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_NOT_FOUND"));
    }

    private ResultActions search(TestUser caller, String email) throws Exception {
        return api.postJson(caller, USERS + "search", """
                {"email": "%s"}""".formatted(email));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                {"refreshToken": "%s"}""".formatted(refreshToken)));
    }

    private String auditFor(String targetId) throws Exception {
        return api.get(admin, "/api/v1/admin/audit-events?targetId=" + targetId)
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> auditActionsFor(String targetId) throws Exception {
        return JsonPath.read(auditFor(targetId), "$.items[*].action");
    }

    private List<String> notificationTypes(TestUser owner) throws Exception {
        return JsonPath.read(api.get(owner, "/api/v1/notifications").andReturn().getResponse().getContentAsString(),
                "$.items[*].type");
    }
}
