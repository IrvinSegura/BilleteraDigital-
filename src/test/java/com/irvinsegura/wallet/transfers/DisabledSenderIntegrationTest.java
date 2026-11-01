package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Access tokens stay valid for up to 15 minutes, so disabling an account is only immediate because money movements
 * check the account under a lock: a transfer that starts while the disable is committing waits for it and is
 * rejected, instead of reading the old status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DisabledSenderIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aTransferWaitsForTheSendersDisableAndIsRejected() throws Exception {
        ApiClient api = new ApiClient(mockMvc, mailpit);
        TestUser alice = api.newUser();
        TestUser bob = api.newUser();
        String aliceWallet = api.openWalletId(alice, "MXN");
        api.openWalletId(bob, "MXN");
        api.deposit(alice, aliceWallet, "100.00").andExpect(status().isCreated());
        UUID aliceId = UUID.fromString(read(api.get(alice, "/api/v1/users/me"), "$.id"));

        CountDownLatch disabled = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        MockHttpServletResponse response;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> disable = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbcTemplate.update("""
                                UPDATE users SET status = 'DISABLED', version = version + 1 WHERE id = ?""", aliceId);
                        disabled.countDown();
                        await(commit);
                    }));
            disabled.await();

            Future<MockHttpServletResponse> transfer = executor.submit(
                    () -> api.transfer(alice, aliceWallet, bob.email(), "30.00", newKey()).andReturn().getResponse());
            try {
                awaitWaitingForLock(transfer);
            } finally {
                // Never leave the disable open, so a failure does not also hang the test
                commit.countDown();
            }
            disable.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            response = transfer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("ACCOUNT_DISABLED");
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("100.00");
    }

    // Asks PostgreSQL instead of timing the request, like IncomingMoneyFreezeIntegrationTest
    private void awaitWaitingForLock(Future<?> operation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (sessionsWaitingForLock() == 0) {
            assertThat(operation.isDone()).as("the transfer finished without waiting for the disable").isFalse();
            assertThat(System.nanoTime() - deadline).as("the transfer never waited for the disable").isNegative();
            Thread.sleep(20);
        }
    }

    private long sessionsWaitingForLock() {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock'""", Long.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the transfer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
