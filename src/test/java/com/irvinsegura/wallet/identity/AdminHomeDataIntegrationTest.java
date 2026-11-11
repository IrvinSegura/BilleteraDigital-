package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the charts of the administration home read. The database is shared with other tests, so these only count up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminHomeDataIntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("America/Mexico_City")).toString();

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
    void todaysSignUpsOperationsAndCustomerMoneyShow() throws Exception {
        TestUser admin = api.admin();
        TestUser customer = api.newUser();
        String walletId = api.openWalletId(customer, "MXN");
        api.deposit(customer, walletId, "10.00").andExpect(status().isCreated());

        String signups = body(api.get(admin, "/api/v1/admin/users/signups?days=7").andExpect(status().isOk()));
        List<Integer> today = JsonPath.read(signups, "$[?(@.date == '" + TODAY + "')].count");
        assertThat(today).hasSize(1);
        assertThat(today.getFirst()).isPositive();

        String daily = body(api.get(admin, "/api/v1/admin/operations/daily?days=7").andExpect(status().isOk()));
        List<Map<String, Object>> deposits = JsonPath.read(daily,
                "$[?(@.date == '" + TODAY + "' && @.kind == 'deposit' && @.amount.currency == 'MXN')]");
        assertThat(deposits).hasSize(1);
        assertThat((Integer) deposits.getFirst().get("count")).isPositive();

        String balances = body(api.get(admin, "/api/v1/admin/ledger/customer-balances").andExpect(status().isOk()));
        List<Integer> mxn = JsonPath.read(balances, "$[?(@.currency == 'MXN')].balanceMinor");
        assertThat(mxn).hasSize(1);
        assertThat(mxn.getFirst()).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void onlyAdministratorsReadThemAndWithinTheLimits() throws Exception {
        TestUser admin = api.admin();
        TestUser customer = api.newUser();

        api.get(customer, "/api/v1/admin/users/signups").andExpect(status().isForbidden());
        api.get(customer, "/api/v1/admin/operations/daily").andExpect(status().isForbidden());
        api.get(customer, "/api/v1/admin/ledger/customer-balances").andExpect(status().isForbidden());
        api.get(admin, "/api/v1/admin/users/signups?days=181").andExpect(status().isBadRequest());
        api.get(admin, "/api/v1/admin/operations/daily?days=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    private static String body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
