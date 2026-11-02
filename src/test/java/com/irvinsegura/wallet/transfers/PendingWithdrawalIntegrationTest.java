package com.irvinsegura.wallet.transfers;

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
import com.irvinsegura.wallet.transfers.application.PendingWithdrawalService;
import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Withdrawals whose payout the bank takes its time with, refuses, or never received, against a payout provider the
 * test controls. Pending withdrawals are picked up at once ({@code retry-after=0s}).
 */
@SpringBootTest(properties = "wallet.transfers.payouts.retry-after=0s")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PendingWithdrawalIntegrationTest.ScriptedPayoutConfiguration.class})
class PendingWithdrawalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private ScriptedPayoutGateway payouts;

    @Autowired
    private PendingWithdrawalService pendingWithdrawals;

    @Autowired
    private ReconciliationService reconciliation;

    @Autowired
    private MeterRegistry meterRegistry;

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

    @AfterEach
    void providerIsBack() {
        payouts.down.set(false);
    }

    @Test
    void theMoneyStaysInTransitUntilTheBankConfirmsThePayout() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "300.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.balance.amount").value("700.00")), "$.id");
        assertThat(api.balance(user, walletId)).isEqualTo("700.00");
        // Money in transit is still owed by the platform, so the ledger keeps balancing
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();

        PayoutOrder order = payouts.orderFor(withdrawalId);
        assertThat(order.destination().clabe()).hasSize(18).startsWith("002");
        assertThat(order.destination().holderName()).isEqualTo("Ana López");
        assertThat(order.amount().toDecimal()).isEqualByComparingTo("300.00");

        payouts.settle(withdrawalId, PayoutResult.Status.PAID, null);
        assertThat(pendingWithdrawals.advancePendingWithdrawals()).isGreaterThanOrEqualTo(1);

        withdrawal(withdrawalId)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        assertThat(api.balance(user, walletId)).isEqualTo("700.00");
        assertEventually(() -> assertThat(notificationTypes()).contains("WITHDRAWAL_SENT"));
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void aRefusedPayoutGivesTheMoneyBackEvenToAFrozenWallet() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "250.00").andExpect(status().isAccepted()), "$.id");
        TestUser admin = api.admin();
        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/freeze", """
                {"reason": "Bank details under review"}""").andExpect(status().isOk());

        payouts.settle(withdrawalId, PayoutResult.Status.FAILED, "ACCOUNT_CLOSED");
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("ACCOUNT_CLOSED"));
        assertThat(api.balance(user, walletId)).isEqualTo("1000.00");
        api.get(user, "/api/v1/wallets/" + walletId + "/movements")
                .andExpect(jsonPath("$.items[0].type").value("WITHDRAWAL_REVERSAL"))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].operationId").value(withdrawalId));
        assertEventually(() -> assertThat(notificationTypes()).contains("WITHDRAWAL_FAILED"));
        assertEventually(() -> assertThat(auditActions(admin)).contains("WITHDRAWAL_FAILED"));
        assertThat(meterRegistry.get("wallet.withdrawals.failed").tag("reason", "ACCOUNT_CLOSED").counter().count())
                .isPositive();
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void aRefusedPayoutNoLongerCountsTowardTheDailyLimit() throws Exception {
        // The test limit is 500.00 MXN a day
        String refusedId = read(api.withdraw(user, walletId, "400.00").andExpect(status().isAccepted()), "$.id");
        api.withdraw(user, walletId, "200.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));

        payouts.settle(refusedId, PayoutResult.Status.FAILED, "INVALID_ACCOUNT");
        pendingWithdrawals.advancePendingWithdrawals();

        api.withdraw(user, walletId, "500.00").andExpect(status().isAccepted());
    }

    @Test
    void aPayoutTheProviderNeverReceivedIsSentAgain() throws Exception {
        payouts.down.set(true);
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");

        // Still down: nothing changes and the job tries again next time
        pendingWithdrawals.advancePendingWithdrawals();
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PENDING"));

        payouts.down.set(false);
        payouts.settle(withdrawalId, PayoutResult.Status.PAID, null);
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(payouts.timesSent(withdrawalId)).isEqualTo(1);
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");
    }

    private ResultActions withdrawal(String withdrawalId) throws Exception {
        return api.get(user, "/api/v1/wallets/" + walletId + "/withdrawals/" + withdrawalId)
                .andExpect(status().isOk());
    }

    private List<String> notificationTypes() throws Exception {
        return JsonPath.read(api.get(user, "/api/v1/notifications").andReturn().getResponse().getContentAsString(),
                "$.items[*].type");
    }

    private List<String> auditActions(TestUser admin) throws Exception {
        return JsonPath.read(api.get(admin, "/api/v1/admin/audit-events?targetId=" + walletId)
                .andReturn().getResponse().getContentAsString(), "$.items[*].action");
    }

    /**
     * Accepts every payout and leaves it pending until the test settles it; can be taken down.
     */
    static class ScriptedPayoutGateway implements PayoutGateway {

        private final AtomicBoolean down = new AtomicBoolean();
        private final Map<String, AtomicInteger> sends = new ConcurrentHashMap<>();
        private final Map<String, PayoutResult> outcomes = new ConcurrentHashMap<>();
        private final Map<String, PayoutOrder> orders = new ConcurrentHashMap<>();

        void settle(String withdrawalId, PayoutResult.Status status, String failureReason) {
            String reference = reference(withdrawalId);
            outcomes.put(reference, new PayoutResult(reference, status, failureReason));
        }

        PayoutOrder orderFor(String withdrawalId) {
            return orders.get(reference(withdrawalId));
        }

        int timesSent(String withdrawalId) {
            AtomicInteger count = sends.get(reference(withdrawalId));
            return count == null ? 0 : count.get();
        }

        @Override
        public PayoutResult send(PayoutOrder order) {
            if (down.get()) {
                throw new PayoutProviderUnavailableException();
            }
            String reference = reference(order.withdrawalId().toString());
            orders.put(reference, order);
            sends.computeIfAbsent(reference, key -> new AtomicInteger()).incrementAndGet();
            return check(reference);
        }

        @Override
        public PayoutResult check(String reference) {
            if (down.get()) {
                throw new PayoutProviderUnavailableException();
            }
            return outcomes.getOrDefault(reference, PayoutResult.pending(reference));
        }

        private static String reference(String withdrawalId) {
            return "po_" + withdrawalId;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedPayoutConfiguration {

        @Bean
        @Primary
        ScriptedPayoutGateway scriptedPayoutGateway() {
            return new ScriptedPayoutGateway();
        }
    }
}
