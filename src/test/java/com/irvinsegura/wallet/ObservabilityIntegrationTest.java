package com.irvinsegura.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests share the application port with actuator, so these are the rules without a separate management port.
 * Metrics export stays on in tests because spring-boot-micrometer-metrics-test, which turns it off, is not used.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void metricsAreForAdministratorsOnly() throws Exception {
        ApiClient api = new ApiClient(mockMvc);

        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        api.get(api.newUser(), "/actuator/prometheus").andExpect(status().isForbidden());
        api.get(api.admin(), "/actuator/prometheus").andExpect(status().isOk());
        api.get(api.admin(), "/actuator/modulith").andExpect(status().isOk());
    }

    @Test
    void exposesBusinessAndOperationalMetrics() throws Exception {
        ApiClient api = new ApiClient(mockMvc);
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "25.00").andExpect(status().isCreated());
        api.withdraw(user, walletId, "1000.00").andExpect(status().isUnprocessableContent());

        String metrics = api.get(api.admin(), "/actuator/prometheus").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics)
                .containsPattern("wallet_money_moved_count\\{[^}]*type=\"deposit\"")
                .containsPattern("wallet_business_rejections_total\\{[^}]*code=\"(INSUFFICIENT_FUNDS|DAILY_LIMIT_EXCEEDED)\"")
                .contains("wallet_events_pending")
                .contains("wallet_events_oldest_pending_age_seconds")
                .contains("wallet_ledger_reconciliation_discrepancies")
                .contains("wallet_ledger_reconciliation_last_run_seconds")
                .containsPattern("http_server_requests_seconds_bucket\\{[^}]*application=\"digital-wallet\"");
    }
}
