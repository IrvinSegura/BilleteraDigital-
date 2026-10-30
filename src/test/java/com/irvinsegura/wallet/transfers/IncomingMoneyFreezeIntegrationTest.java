package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A freeze that commits while money is coming in must win: the operation waits for it and is rejected, instead of
 * reading the old status and crediting a wallet that is frozen a moment later.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IncomingMoneyFreezeIntegrationTest {

    // Long enough for the request to reach the lock, short enough to keep the test fast
    private static final long BLOCKED_FOR_MILLIS = 500;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ApiClient api;
    private TestUser alice;
    private TestUser bob;
    private String aliceWallet;
    private String bobWallet;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        alice = api.newUser();
        bob = api.newUser();
        aliceWallet = api.openWalletId(alice, "MXN");
        bobWallet = api.openWalletId(bob, "MXN");
        api.deposit(alice, aliceWallet, "100.00").andExpect(status().isCreated());
    }

    @Test
    void aTransferWaitsForTheRecipientsFreezeAndIsRejected() throws Exception {
        MockHttpServletResponse response = whileFreezing(bobWallet,
                () -> api.transfer(alice, aliceWallet, bob.email(), "30.00", newKey()).andReturn().getResponse());

        assertRejectedAsFrozen(response);
        assertThat(api.balance(bob, bobWallet)).isEqualTo("0.00");
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("100.00");
    }

    @Test
    void aDepositWaitsForTheWalletsFreezeAndIsRejected() throws Exception {
        MockHttpServletResponse response = whileFreezing(bobWallet,
                () -> api.deposit(bob, bobWallet, "30.00").andReturn().getResponse());

        assertRejectedAsFrozen(response);
        assertThat(api.balance(bob, bobWallet)).isEqualTo("0.00");
    }

    /**
     * Freezes the wallet in a transaction kept open until the operation is shown to be blocked by it.
     */
    private MockHttpServletResponse whileFreezing(String walletId, Callable<MockHttpServletResponse> operation)
            throws Exception {
        CountDownLatch frozen = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> freeze = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbcTemplate.update("""
                                UPDATE wallets SET status = 'FROZEN', version = version + 1 WHERE id = ?""",
                                UUID.fromString(walletId));
                        frozen.countDown();
                        await(commit);
                    }));
            frozen.await();

            Future<MockHttpServletResponse> result = executor.submit(operation);
            assertThatThrownBy(() -> result.get(BLOCKED_FOR_MILLIS, TimeUnit.MILLISECONDS))
                    .as("the operation must wait for the freeze in progress")
                    .isInstanceOf(TimeoutException.class);

            commit.countDown();
            freeze.get(10, TimeUnit.SECONDS);
            return result.get(10, TimeUnit.SECONDS);
        }
    }

    private static void assertRejectedAsFrozen(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("WALLET_NOT_ACTIVE");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the operation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
