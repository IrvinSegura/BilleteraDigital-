package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

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
        api = new ApiClient(mockMvc, mailpit);
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
     * Freezes the wallet in a transaction kept open until the operation is waiting for it, then commits the freeze.
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
            try {
                awaitWaitingForLock(result);
            } finally {
                // Never leave the freeze open, so a failure does not also hang the test
                commit.countDown();
            }
            freeze.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Asks PostgreSQL instead of timing the request: passes once the operation's statement waits for a row lock, and
     * fails as soon as the operation finishes without waiting, which is what the race looked like.
     */
    private void awaitWaitingForLock(Future<?> operation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (sessionsWaitingForLock() == 0) {
            assertThat(operation.isDone()).as("the operation finished without waiting for the freeze").isFalse();
            assertThat(System.nanoTime() - deadline).as("the operation never waited for the freeze").isNegative();
            Thread.sleep(20);
        }
    }

    private long sessionsWaitingForLock() {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock'""", Long.class);
    }

    private static void assertRejectedAsFrozen(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("WALLET_NOT_ACTIVE");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the operation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
