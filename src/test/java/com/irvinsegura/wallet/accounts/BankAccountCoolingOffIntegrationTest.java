package com.irvinsegura.wallet.accounts;

import static com.irvinsegura.wallet.support.ApiClient.newClabe;
import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The waiting period of new bank accounts, which the test profile turns off for every other test.
 */
@SpringBootTest(properties = "wallet.accounts.bank-account-cooling-off=24h")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BankAccountCoolingOffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aNewBankAccountReceivesWithdrawalsOnlyAfterItsWaitingPeriod() throws Exception {
        ApiClient api = new ApiClient(mockMvc, mailpit);
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "100.00").andExpect(status().isCreated());

        String body = api.addBankAccount(user, newClabe()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bankAccountId = JsonPath.read(body, "$.id");
        Instant createdAt = Instant.parse(JsonPath.<String>read(body, "$.createdAt"));
        assertThat(Instant.parse(JsonPath.<String>read(body, "$.withdrawalsAvailableFrom")))
                .isEqualTo(createdAt.plus(Duration.ofHours(24)));

        api.withdrawTo(user, walletId, "10.00", newKey(), bankAccountId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_COOLING_OFF"));
        assertThat(api.balance(user, walletId)).isEqualTo("100.00");

        // A day later
        jdbcTemplate.update("UPDATE bank_accounts SET created_at = created_at - interval '25 hours' WHERE id = ?",
                UUID.fromString(bankAccountId));
        api.withdrawTo(user, walletId, "10.00", newKey(), bankAccountId).andExpect(status().isCreated());
    }
}
