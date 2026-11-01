package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WalletDepositIntegrationTest {

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
    void newWalletStartsEmptyAndShowsDepositsInItsStatement() throws Exception {
        TestUser user = api.newUser();
        String walletId = read(api.openWallet(user, "MXN")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/wallets/")))
                .andExpect(jsonPath("$.balance.amount").value("0.00"))
                .andExpect(jsonPath("$.balance.currency").value("MXN"))
                .andExpect(jsonPath("$.status").value("ACTIVE")), "$.id");

        // The simulated provider confirms the payment at once, so the deposit completes within the request
        String depositId = read(api.deposit(user, walletId, "100.50")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.amount.amount").value("100.50"))
                .andExpect(jsonPath("$.balance.amount").value("100.50")), "$.id");
        api.deposit(user, walletId, "20").andExpect(jsonPath("$.balance.amount").value("120.50"));

        assertThat(api.balance(user, walletId)).isEqualTo("120.50");
        api.get(user, "/api/v1/wallets/" + walletId + "/deposits/" + depositId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balance.amount").value("100.50"));
        api.get(user, "/api/v1/wallets/" + walletId + "/movements?page=1&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[0].operationId").value(depositId))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].amount.amount").value("100.50"))
                .andExpect(jsonPath("$.items[0].balanceAfter.amount").value("100.50"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void onlyOneWalletPerCurrencyAndOnlySupportedCurrencies() throws Exception {
        TestUser user = api.newUser();
        api.openWallet(user, "MXN").andExpect(status().isCreated());
        api.openWallet(user, "USD").andExpect(status().isCreated());

        api.openWallet(user, "MXN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));
        api.openWallet(user, "EUR")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        api.openWallet(user, "mxn").andExpect(status().isBadRequest());

        api.get(user, "/api/v1/wallets").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void usersCannotSeeOrFundOtherUsersWallets() throws Exception {
        TestUser owner = api.newUser();
        TestUser stranger = api.newUser();
        String walletId = api.openWalletId(owner, "MXN");

        api.get(stranger, "/api/v1/wallets/" + walletId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
        api.deposit(stranger, walletId, "10.00").andExpect(status().isNotFound());
        String depositId = read(api.deposit(owner, walletId, "10.00"), "$.id");
        api.get(stranger, "/api/v1/wallets/" + walletId + "/deposits/" + depositId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidAmounts() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");

        api.deposit(user, walletId, "-5.00").andExpect(status().isBadRequest());
        api.deposit(user, walletId, "0").andExpect(status().isBadRequest());
        // MXN has two decimals: rejected instead of silently rounded
        api.deposit(user, walletId, "10.505")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void retriedDepositIsAppliedOnce() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        String key = newKey();

        String first = read(api.deposit(user, walletId, "50.00", key).andExpect(status().isCreated()), "$.id");
        String retry = read(api.deposit(user, walletId, "50", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.balance.amount").value("50.00")), "$.id");

        assertThat(retry).isEqualTo(first);
        assertThat(api.balance(user, walletId)).isEqualTo("50.00");
    }

    @Test
    void depositsRequireAValidIdempotencyKey() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");

        api.deposit(user, walletId, "10.00", "not a valid key!").andExpect(status().isBadRequest());
        api.deposit(user, walletId, "10.00", "a".repeat(101)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentDepositsAreAllCounted() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");

        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<Callable<Integer>> deposits = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                deposits.add(() -> api.deposit(user, walletId, "10.00").andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : executor.invokeAll(deposits)) {
                assertThat(result.get()).isEqualTo(201);
            }
        }

        assertThat(api.balance(user, walletId)).isEqualTo("250.00");
    }

    @Test
    void walletsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/wallets")).andExpect(status().isUnauthorized());
    }
}
