package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.ledger.application.ReconciliationService;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.FakeOpenpay;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.irvinsegura.wallet.transfers.application.PendingWithdrawalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * MXN withdrawals paid to a CLABE with Openpay as the payout provider, against {@link FakeOpenpay}: the real HTTP
 * client, its authentication and the real database. Pending withdrawals are picked up at once ({@code retry-after=0s}).
 */
@SpringBootTest(properties = {
        "wallet.transfers.payouts.provider=openpay",
        "wallet.transfers.payouts.retry-after=0s",
        "wallet.transfers.payouts.openpay.merchant-id=" + FakeOpenpay.MERCHANT_ID,
        "wallet.transfers.payouts.openpay.private-key=" + FakeOpenpay.PRIVATE_KEY})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OpenpayPayoutIntegrationTest.FakeOpenpayConfiguration.class})
class OpenpayPayoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private FakeOpenpay openpay;

    @Autowired
    private PendingWithdrawalService pendingWithdrawals;

    @Autowired
    private ReconciliationService reconciliation;

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
    void openpayIsBack() {
        openpay.setDown(false);
        openpay.setOutOfFunds(false);
    }

    @Test
    void aWithdrawalIsPaidOnceOpenpayCompletesIt() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "300.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");

        JsonNode payout = openpay.lastPayout();
        assertThat(payout.path("method").asString()).isEqualTo("bank_account");
        assertThat(payout.path("bank_account").path("clabe").asString()).hasSize(18).startsWith("002");
        assertThat(payout.path("bank_account").path("holder_name").asString()).isEqualTo("Ana López");
        assertThat(payout.path("amount").decimalValue()).isEqualByComparingTo("300.00");
        // The withdrawal's id: Openpay takes each order once
        assertThat(payout.path("order_id").asString()).isEqualTo(withdrawalId);

        // Still on its way: the job asks and leaves it pending
        pendingWithdrawals.advancePendingWithdrawals();
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PENDING"));

        openpay.settle(openpay.idOf(withdrawalId), "completed");
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(api.balance(user, walletId)).isEqualTo("700.00");
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void aPayoutThatFailsGivesTheMoneyBack() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "250.00").andExpect(status().isAccepted()), "$.id");

        openpay.settle(openpay.idOf(withdrawalId), "failed");
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("OPENPAY_FAILED"));
        assertThat(api.balance(user, walletId)).isEqualTo("1000.00");
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void aPayoutOpenpayRefusesFailsAndGivesTheMoneyBack() throws Exception {
        // 1007: the bank account didn't accept the transfer
        openpay.rejectNext(402, 1007);

        api.withdraw(user, walletId, "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("OPENPAY_REJECTED_1007"));

        assertThat(api.balance(user, walletId)).isEqualTo("1000.00");
    }

    @Test
    void aPayoutWaitsWhileThePlatformsBalanceIsShort() throws Exception {
        openpay.setOutOfFunds(true);
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");

        // Not the customer's doing: it is sent again once the balance is topped up
        openpay.setOutOfFunds(false);
        pendingWithdrawals.advancePendingWithdrawals();
        openpay.settle(openpay.idOf(withdrawalId), "completed");
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void aPayoutWaitsWhileTheMerchantMayNotMakePayouts() throws Exception {
        // 3006: Openpay hasn't enabled payouts for the merchant, which no customer can fix
        openpay.rejectNext(412, 3006);

        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");

        // Once they are enabled, it is sent again and paid
        pendingWithdrawals.advancePendingWithdrawals();
        openpay.settle(openpay.idOf(withdrawalId), "completed");
        pendingWithdrawals.advancePendingWithdrawals();
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void aPayoutWhoseAnswerWasLostIsNotMadeTwice() throws Exception {
        int before = openpay.created();
        openpay.loseNextAnswer();
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");

        // The job sends it again, Openpay answers that the order is taken, and the payout already made is followed
        pendingWithdrawals.advancePendingWithdrawals();
        openpay.settle(openpay.idOf(withdrawalId), "completed");
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(openpay.created() - before).isEqualTo(1);
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");
    }

    @Test
    void aPayoutIsSentAgainWhenOpenpayWasDown() throws Exception {
        int before = openpay.created();
        openpay.setDown(true);
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(openpay.created()).isEqualTo(before);

        openpay.setDown(false);
        pendingWithdrawals.advancePendingWithdrawals();

        assertThat(openpay.created() - before).isEqualTo(1);
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void dollarsCannotBeWithdrawnSinceOpenpayPaysOnlyPesos() throws Exception {
        api.get(user, "/api/v1/withdrawals/currencies").andExpect(jsonPath("$.currencies.length()").value(1))
                .andExpect(jsonPath("$.currencies[0]").value("MXN"));
        String dollars = api.openWalletId(user, "USD");
        api.deposit(user, dollars, "100.00").andExpect(status().isCreated());
        int before = openpay.created();

        api.withdraw(user, dollars, "40.00").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_CURRENCY_NOT_SUPPORTED"));

        assertThat(api.balance(user, dollars)).isEqualTo("100.00");
        assertThat(openpay.created()).isEqualTo(before);
    }

    private ResultActions withdrawal(String withdrawalId) throws Exception {
        return api.get(user, "/api/v1/wallets/" + walletId + "/withdrawals/" + withdrawalId)
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeOpenpayConfiguration {

        @Bean
        FakeOpenpay fakeOpenpay() {
            return new FakeOpenpay();
        }

        @Bean
        DynamicPropertyRegistrar openpayProperties(FakeOpenpay openpay) {
            return registry -> registry.add("wallet.transfers.payouts.openpay.url", openpay::url);
        }
    }
}
