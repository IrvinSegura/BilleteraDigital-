package com.irvinsegura.wallet.accounts;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminWalletIntegrationTest {

    private static final String REASON = """
            {"reason": "Suspicious activity reported by the bank"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private TestUser admin;
    private TestUser owner;
    private String walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
        owner = api.newUser();
        walletId = api.openWalletId(owner, "MXN");
        api.deposit(owner, walletId, "100.00").andExpect(status().isCreated());
    }

    @Test
    void adminAccountIsCreatedFromConfiguration() throws Exception {
        api.get(admin, "/api/v1/users/me").andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void regularUsersCannotUseAdminEndpoints() throws Exception {
        api.get(owner, "/api/v1/admin/wallets/" + walletId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.postJson(owner, "/api/v1/admin/wallets/" + walletId + "/freeze", REASON)
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAnyWallet() throws Exception {
        api.get(admin, "/api/v1/admin/wallets/" + walletId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.amount").value("100.00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void frozenWalletCannotMoveMoneyUntilUnfrozen() throws Exception {
        TestUser friend = api.newUser();
        String friendWallet = api.openWalletId(friend, "MXN");
        api.deposit(friend, friendWallet, "50.00");

        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/freeze", REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        api.deposit(owner, walletId, "1.00").andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
        api.withdraw(owner, walletId, "1.00").andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
        api.transfer(owner, walletId, friend.email(), "1.00", newKey())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
        // Incoming transfers are blocked too
        api.transfer(friend, friendWallet, owner.email(), "1.00", newKey())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
        // The owner still sees the wallet and its balance
        api.get(owner, "/api/v1/wallets/" + walletId)
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.balance.amount").value("100.00"));

        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/unfreeze", REASON)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        api.withdraw(owner, walletId, "1.00").andExpect(status().isCreated());
    }

    @Test
    void freezingRequiresAReason() throws Exception {
        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/freeze", """
                        {"reason": " "}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
