package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The test profile caps outgoing money at 500.00 MXN per wallet and day.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WithdrawalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private TestUser user;
    private String walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        user = api.newUser();
        walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "1000.00").andExpect(status().isCreated());
    }

    @Test
    void withdrawalLowersTheBalanceAndAppearsInTheStatement() throws Exception {
        // The simulated provider pays at once, so the withdrawal is paid within the request
        String withdrawalId = read(api.withdraw(user, walletId, "120.50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amount.amount").value("120.50"))
                .andExpect(jsonPath("$.balance.amount").value("879.50")), "$.id");

        // Paying out moves the money from transit to the bank; the wallet sees only the money leaving it
        api.get(user, "/api/v1/wallets/" + walletId + "/movements")
                .andExpect(jsonPath("$.items[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[0].direction").value("OUT"))
                .andExpect(jsonPath("$.items[0].operationId").value(withdrawalId))
                .andExpect(jsonPath("$.items[1].type").value("DEPOSIT"));
        api.get(user, "/api/v1/wallets/" + walletId + "/withdrawals/" + withdrawalId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        api.get(api.newUser(), "/api/v1/wallets/" + walletId + "/withdrawals/" + withdrawalId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_NOT_FOUND"));
    }

    @Test
    void mxnWithdrawalsGoToOneOfTheUsersOwnBankAccounts() throws Exception {
        api.withdrawTo(user, walletId, "10.00", newKey(), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_REQUIRED"));

        TestUser stranger = api.newUser();
        String strangersAccount = read(api.addBankAccount(stranger, ApiClient.newClabe()), "$.id");
        api.withdrawTo(user, walletId, "10.00", newKey(), strangersAccount)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_NOT_FOUND"));

        String ownAccount = read(api.addBankAccount(user, ApiClient.newClabe()), "$.id");
        api.withdrawTo(user, walletId, "10.00", newKey(), ownAccount)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankAccountId").value(ownAccount));
        assertThat(api.balance(user, walletId)).isEqualTo("990.00");
    }

    @Test
    void usdWithdrawalsTakeNoBankAccount() throws Exception {
        String usdWallet = api.openWalletId(user, "USD");
        api.deposit(user, usdWallet, "50.00").andExpect(status().isCreated());
        String account = read(api.addBankAccount(user, ApiClient.newClabe()), "$.id");

        api.withdrawTo(user, usdWallet, "10.00", newKey(), account)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_NOT_SUPPORTED"));
        api.withdrawTo(user, usdWallet, "10.00", newKey(), null).andExpect(status().isCreated());
    }

    @Test
    void retriedWithdrawalIsAppliedOnce() throws Exception {
        String key = newKey();
        api.withdraw(user, walletId, "100.00", key).andExpect(status().isCreated());

        api.withdraw(user, walletId, "100.00", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");
    }

    @Test
    void cannotWithdrawMoreThanTheBalance() throws Exception {
        TestUser poor = api.newUser();
        String poorWallet = api.openWalletId(poor, "MXN");
        api.deposit(poor, poorWallet, "10.00");

        api.withdraw(poor, poorWallet, "10.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void withdrawalsAndTransfersShareTheDailyLimit() throws Exception {
        TestUser friend = api.newUser();
        api.openWalletId(friend, "MXN");

        api.withdraw(user, walletId, "300.00").andExpect(status().isCreated());
        api.transfer(user, walletId, friend.email(), "200.00", newKey()).andExpect(status().isCreated());

        api.withdraw(user, walletId, "0.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        api.transfer(user, walletId, friend.email(), "0.01", newKey())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        // Deposits are not limited
        api.deposit(user, walletId, "50.00").andExpect(status().isCreated());
    }

    @Test
    void failedOperationsDoNotUseUpTheLimit() throws Exception {
        TestUser poor = api.newUser();
        String poorWallet = api.openWalletId(poor, "MXN");
        api.deposit(poor, poorWallet, "100.00");

        api.withdraw(poor, poorWallet, "400.00").andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        api.deposit(poor, poorWallet, "500.00");

        // The failed 400.00 rolled back with its transaction, so the full 500.00 is still available
        api.withdraw(poor, poorWallet, "500.00").andExpect(status().isCreated());
    }

    @Test
    void concurrentWithdrawalsCannotBeatTheLimit() throws Exception {
        // 10 x 100.00 against a 500.00 limit, all at once: the wallet row lock lets exactly 5 through
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<Callable<Integer>> withdrawals = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                withdrawals.add(() -> api.withdraw(user, walletId, "100.00").andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : executor.invokeAll(withdrawals)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 422).hasSize(5);
        assertThat(api.balance(user, walletId)).isEqualTo("500.00");
    }
}
