package com.irvinsegura.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LedgerReconciliationIntegrationTest {

    // Same key as LedgerIntegrityJdbcAdapter, to hold the lock from the test
    private static final long RECONCILIATION_LOCK_KEY = 0x4C45444745520001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ApiClient api;
    private TestUser admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc);
        admin = api.admin();
    }

    @Test
    void ledgerBuiltThroughTheApiReconcilesCleanly() throws Exception {
        TestUser alice = api.newUser();
        TestUser bob = api.newUser();
        String aliceWallet = api.openWalletId(alice, "MXN");
        api.openWalletId(bob, "MXN");
        api.deposit(alice, aliceWallet, "300.00").andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bob.email(), "120.50", ApiClient.newKey()).andExpect(status().isCreated());
        api.withdraw(alice, aliceWallet, "50.00").andExpect(status().isCreated());

        String body = reconcile().andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONSISTENT"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.discrepancies").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.accountsChecked")).longValue()).isGreaterThanOrEqualTo(4);
        assertThat(((Number) JsonPath.read(body, "$.entriesChecked")).longValue()).isGreaterThanOrEqualTo(3);
        assertThat((String) JsonPath.read(body, "$.triggeredBy"))
                .isEqualTo(ApiClient.read(api.get(admin, "/api/v1/users/me"), "$.id"));
    }

    @Test
    void balanceChangedOutsideTheLedgerIsReported() throws Exception {
        TestUser owner = api.newUser();
        String walletId = api.openWalletId(owner, "USD");
        api.deposit(owner, walletId, "100.00").andExpect(status().isCreated());
        UUID ledgerAccount = jdbcTemplate.queryForObject(
                "SELECT ledger_account_id FROM wallets WHERE id = ?", UUID.class, UUID.fromString(walletId));

        // Someone edits a cached balance directly in the database, bypassing the journal
        jdbcTemplate.update("UPDATE ledger_accounts SET balance_minor = balance_minor + 1 WHERE id = ?", ledgerAccount);
        try {
            String body = reconcile().andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DISCREPANCIES_FOUND"))
                    .andReturn().getResponse().getContentAsString();

            List<Map<String, Object>> discrepancies = JsonPath.read(body, "$.discrepancies");
            assertThat(discrepancies).anySatisfy(discrepancy -> {
                assertThat(discrepancy).containsEntry("kind", "ACCOUNT_BALANCE")
                        .containsEntry("subjectId", ledgerAccount.toString())
                        .containsEntry("currency", "USD");
                assertThat(((Number) discrepancy.get("actualMinor")).longValue())
                        .isEqualTo(((Number) discrepancy.get("expectedMinor")).longValue() + 1);
            });
            // Liabilities now exceed the settlement asset by one cent
            assertThat(discrepancies).anySatisfy(discrepancy -> assertThat(discrepancy)
                    .containsEntry("kind", "TRIAL_BALANCE")
                    .containsEntry("currency", "USD"));
        } finally {
            jdbcTemplate.update("UPDATE ledger_accounts SET balance_minor = balance_minor - 1 WHERE id = ?",
                    ledgerAccount);
        }

        reconcile().andExpect(jsonPath("$.status").value("CONSISTENT"));
        api.get(admin, "/api/v1/admin/reconciliations?limit=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONSISTENT"))
                .andExpect(jsonPath("$[1].status").value("DISCREPANCIES_FOUND"))
                .andExpect(jsonPath("$[1].discrepancies[0].kind").exists());
    }

    @Test
    void onlyOneReconciliationRunsAtATime() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Another instance is reconciling: it holds the lock until its transaction ends
            Future<?> otherInstance = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> null, RECONCILIATION_LOCK_KEY);
                        locked.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            reconcile().andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RECONCILIATION_IN_PROGRESS"));

            release.countDown();
            otherInstance.get(10, TimeUnit.SECONDS);
            reconcile().andExpect(status().isCreated());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reconciliationsAreAdminOnlyAndAppendOnly() throws Exception {
        TestUser user = api.newUser();
        api.postJson(user, "/api/v1/admin/reconciliations", "").andExpect(status().isForbidden());
        reconcile().andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_reconciliation_runs SET discrepancy_count = 0"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_reconciliation_runs"))
                .hasMessageContaining("append-only");
    }

    private ResultActions reconcile() throws Exception {
        return mockMvc.perform(post("/api/v1/admin/reconciliations").header("Authorization", admin.bearer()));
    }
}
