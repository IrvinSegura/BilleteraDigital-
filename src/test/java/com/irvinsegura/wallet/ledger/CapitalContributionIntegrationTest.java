package com.irvinsegura.wallet.ledger;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.ledger.application.ReconciliationService;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CapitalContributionIntegrationTest {

    private static final String CONTRIBUTIONS = "/api/v1/admin/ledger/capital-contributions";
    private static final String SYSTEM_ACCOUNTS = "/api/v1/admin/ledger/system-accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private ReconciliationService reconciliation;

    private ApiClient api;
    private TestUser admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
    }

    @Test
    void capitalFundsTheSettlementAccountOncePerBankReference() throws Exception {
        String reference = "WIRE-" + UUID.randomUUID();
        long settlementBefore = balance("SETTLEMENT", "USD");
        long capitalBefore = balance("PLATFORM_CAPITAL", "USD");

        api.postJson(admin, CONTRIBUTIONS, contribution("USD", "2500.00", reference))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SETTLEMENT"))
                .andExpect(jsonPath("$.balanceMinor").value(settlementBefore + 250_000));
        // A retried request carries the same wire, which must not count twice
        api.postJson(admin, CONTRIBUTIONS, contribution("USD", "2500.00", reference))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPITAL_CONTRIBUTION_EXISTS"));

        assertThat(balance("PLATFORM_CAPITAL", "USD")).isEqualTo(capitalBefore + 250_000);
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
        assertEventually(() -> assertThat(auditDetails()).anyMatch(details -> details.contains(reference)));
    }

    @Test
    void onlyAdministratorsTouchTheSystemAccounts() throws Exception {
        TestUser user = api.newUser();

        api.postJson(user, CONTRIBUTIONS, contribution("MXN", "10.00", "WIRE-" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        api.get(user, SYSTEM_ACCOUNTS).andExpect(status().isForbidden());
        api.postJson(admin, CONTRIBUTIONS, contribution("EUR", "10.00", "WIRE-" + UUID.randomUUID()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        api.get(admin, SYSTEM_ACCOUNTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));
    }

    private static String contribution(String currency, String amount, String reference) {
        return """
                {"currency": "%s", "amount": %s, "reference": "%s", "reason": "Float for payouts"}"""
                .formatted(currency, amount, reference);
    }

    private long balance(String code, String currency) throws Exception {
        List<Number> balances = JsonPath.read(api.get(admin, SYSTEM_ACCOUNTS).andReturn().getResponse()
                .getContentAsString(), "$[?(@.code == '%s' && @.currency == '%s')].balanceMinor"
                .formatted(code, currency));
        return balances.getFirst().longValue();
    }

    private List<String> auditDetails() throws Exception {
        String adminId = read(api.get(admin, "/api/v1/users/me"), "$.id");
        return JsonPath.read(api.get(admin, "/api/v1/admin/audit-events?size=200&actorId=" + adminId)
                .andReturn().getResponse().getContentAsString(),
                "$.items[?(@.action == 'CAPITAL_CONTRIBUTED')].details");
    }
}
