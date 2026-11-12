package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Administrators settling a problem with money: small adjustments apply at once, larger ones wait for a second
 * administrator, and the books stay balanced either way. The test profile lets one administrator adjust up to 300.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdjustmentIntegrationTest {

    private static final String ADJUSTMENTS = "/api/v1/admin/adjustments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcClient jdbc;

    private ApiClient api;
    private TestUser admin;
    private TestUser customer;
    private String walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
        customer = api.newUser();
        walletId = api.openWalletId(customer, "MXN");
        api.deposit(customer, walletId, "100.00").andExpect(status().isCreated());
    }

    @Test
    void aSmallCreditAppliesAtOnceAndTheCustomerIsTold() throws Exception {
        String id = read(adjust(admin, "CREDIT", "40.00", "El depósito del 3 de noviembre no se acreditó")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.secondApproval").value(false))
                .andExpect(jsonPath("$.journalEntryId").exists())
                .andExpect(jsonPath("$.customerEmail").value(customer.email())), "$.id");

        assertThat(api.balance(customer, walletId)).isEqualTo("140.00");
        assertEventually(() -> assertThat(notifications(customer, "WALLET_ADJUSTED")).contains(id));
        assertEventually(() -> assertThat(audit(walletId)).contains("ADJUSTMENT_APPLIED"));
        api.get(customer, "/api/v1/wallets/" + walletId + "/movements")
                .andExpect(jsonPath("$.items[0].type").value("ADJUSTMENT_CREDIT"));
        reconcile();
    }

    @Test
    void aDebitTakesMoneyBackButNeverMoreThanTheBalance() throws Exception {
        adjust(admin, "DEBIT", "150.00", "Se acreditó dos veces")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(api.balance(customer, walletId)).isEqualTo("100.00");

        adjust(admin, "DEBIT", "60.00", "Se acreditó dos veces").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        assertThat(api.balance(customer, walletId)).isEqualTo("40.00");
        reconcile();
    }

    @Test
    void aLargeAdjustmentWaitsForASecondAdministrator() throws Exception {
        String id = read(adjust(admin, "CREDIT", "500.00", "Compensación por el retiro perdido")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.secondApproval").value(true)), "$.id");
        assertThat(api.balance(customer, walletId)).isEqualTo("100.00");
        api.get(admin, ADJUSTMENTS + "?status=PENDING_APPROVAL")
                .andExpect(jsonPath("$.pending").isNumber())
                .andExpect(jsonPath("$.items[?(@.id == '%s')]".formatted(id)).exists());

        // Whoever asked for it cannot also apply it
        api.postJson(admin, ADJUSTMENTS + "/" + id + "/approve", "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADJUSTMENT_SELF_APPROVAL"));

        TestUser second = secondAdmin();
        api.postJson(second, ADJUSTMENTS + "/" + id + "/approve", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.decidedByName").exists());
        assertThat(api.balance(customer, walletId)).isEqualTo("600.00");

        // Decided once only
        api.postJson(second, ADJUSTMENTS + "/" + id + "/approve", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADJUSTMENT_NOT_PENDING"));
        assertEventually(() -> assertThat(audit(walletId)).contains("ADJUSTMENT_REQUESTED", "ADJUSTMENT_APPLIED"));
        reconcile();
    }

    @Test
    void aPendingAdjustmentCanBeTurnedDown() throws Exception {
        String id = read(adjust(admin, "DEBIT", "90.00", "Revisar cargo duplicado").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED")), "$.id");
        assertThat(id).isNotBlank();
        String pending = read(adjust(admin, "CREDIT", "1000.00", "Reembolso grande")
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL")), "$.id");

        // Its requester may withdraw it
        api.postJson(admin, ADJUSTMENTS + "/" + pending + "/reject", """
                        {"reason": "Lo pedí por error"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Lo pedí por error"))
                .andExpect(jsonPath("$.journalEntryId").doesNotExist());
        api.postJson(secondAdmin(), ADJUSTMENTS + "/" + pending + "/approve", "")
                .andExpect(status().isConflict());
        assertThat(api.balance(customer, walletId)).isEqualTo("10.00");
        assertEventually(() -> assertThat(audit(idOf(customer))).contains("ADJUSTMENT_REJECTED"));
    }

    @Test
    void onlyAdministratorsAdjustWallets() throws Exception {
        adjust(customer, "CREDIT", "10.00", "Me lo merezco").andExpect(status().isForbidden());
        api.get(customer, ADJUSTMENTS).andExpect(status().isForbidden());
        adjust(admin, "CREDIT", "10.00", " ").andExpect(status().isBadRequest());
        adjust(admin, "CREDIT", "0", "Nada").andExpect(status().isBadRequest());
        api.postJson(admin, ADJUSTMENTS, """
                        {"walletId": "00000000-0000-0000-0000-000000000000", "direction": "CREDIT", "amount": 5,
                         "reason": "x"}""")
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions adjust(TestUser who, String direction, String amount,
            String reason) throws Exception {
        return api.postJson(who, ADJUSTMENTS, """
                {"walletId": "%s", "direction": "%s", "amount": %s, "reason": "%s"}"""
                .formatted(walletId, direction, amount, reason));
    }

    // Staff accounts are made with a password-reset link; here the role is set directly to keep the test short
    private TestUser secondAdmin() throws Exception {
        TestUser account = api.newUser();
        jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE email = :email").param("email", account.email()).update();
        return api.login(account.email(), PASSWORD);
    }

    private void reconcile() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reconciliations").header("Authorization", admin.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONSISTENT"));
    }

    private String audit(String targetId) throws Exception {
        return api.get(admin, "/api/v1/admin/audit-events?targetId=" + targetId).andReturn().getResponse()
                .getContentAsString();
    }

    private String idOf(TestUser account) throws Exception {
        return read(api.get(account, "/api/v1/users/me"), "$.id");
    }

    private List<String> notifications(TestUser account, String type) throws Exception {
        String body = api.get(account, "/api/v1/notifications").andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[?(@.type == '%s')].referenceId".formatted(type));
    }
}
