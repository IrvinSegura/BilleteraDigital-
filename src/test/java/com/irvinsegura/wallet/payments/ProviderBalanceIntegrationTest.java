package com.irvinsegura.wallet.payments;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.ledger.application.ReconciliationService;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import com.irvinsegura.wallet.payments.application.ProviderBalanceService;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeSignatureVerifier;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.FakeStripe;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The clearing account following Stripe's balance, against {@link FakeStripe}. Same configuration as
 * {@link StripeDepositIntegrationTest}, so both share one application context and one fake Stripe.
 */
@SpringBootTest(properties = {
        "wallet.payments.provider=stripe",
        "wallet.payments.stripe.api-key=sk_test_fake",
        "wallet.payments.stripe.webhook-secret=" + StripeDepositIntegrationTest.WEBHOOK_SECRET})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StripeDepositIntegrationTest.FakeStripeConfiguration.class})
class ProviderBalanceIntegrationTest {

    private static final String WEBHOOK = "/api/v1/payments/webhooks/stripe";
    private static final String TRANSACTIONS = "/api/v1/admin/payments/provider-transactions";

    private final StripeSignatureVerifier signer = new StripeSignatureVerifier(
            StripeDepositIntegrationTest.WEBHOOK_SECRET, Duration.ofMinutes(5), Clock.systemUTC());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private FakeStripe stripe;

    @Autowired
    private ProviderBalanceService providerBalance;

    @Autowired
    private ReconciliationService reconciliation;

    private ApiClient api;
    private TestUser user;
    private TestUser admin;
    private String walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        user = api.newUser();
        admin = api.admin();
        walletId = api.openWalletId(user, "MXN");
    }

    @Test
    void feesAndPayoutsKeepTheClearingAccountInStepWithStripe() throws Exception {
        String session = paidDeposit("100.00");
        Map<String, Long> before = systemBalances("MXN");

        stripe.addBalanceTransaction("charge", 10_000, 380, "mxn", stripe.paymentIntentOf(session));
        stripe.addBalanceTransaction("payout", -5_000, 0, "mxn", null);
        stripe.addBalanceTransaction("payout_failure", 1_000, 0, "mxn", null);
        stripe.setBalance("mxn", before.get("PROVIDER_CLEARING") - 380 - 5_000 + 1_000);
        providerBalance.synchronize();

        Map<String, Long> after = systemBalances("MXN");
        assertThat(after.get("PROVIDER_CLEARING")).isEqualTo(before.get("PROVIDER_CLEARING") - 4_380);
        assertThat(after.get("SETTLEMENT")).isEqualTo(before.get("SETTLEMENT") + 4_000);
        assertThat(after.get("PROVIDER_FEES")).isEqualTo(before.get("PROVIDER_FEES") + 380);
        assertThat(providerBalance.balanceDifferences()).containsEntry("MXN", 0L);
        api.get(admin, "/api/v1/admin/payments/provider-balance-differences")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdsFunds").value(true))
                .andExpect(jsonPath("$.comparisons[?(@.currency == 'MXN')].differenceMinor").value(0))
                .andExpect(jsonPath("$.comparisons[?(@.currency == 'MXN')].recordedBalanceMinor")
                        .value(after.get("PROVIDER_CLEARING").intValue()));
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();

        // Reading the same movements again records nothing twice
        providerBalance.synchronize();
        assertThat(systemBalances("MXN")).isEqualTo(after);
    }

    @Test
    void aChargeWaitsForTheWebhookOfItsPayment() throws Exception {
        String depositId = read(api.deposit(user, walletId, "50.00").andExpect(status().isAccepted()), "$.id");
        String session = stripe.sessionFor(depositId);
        stripe.pay(session);
        String chargeId = stripe.addBalanceTransaction("charge", 5_000, 200, "mxn", stripe.paymentIntentOf(session));

        providerBalance.synchronize();
        assertThat(transactionIds("PENDING")).contains(chargeId);

        sendWebhook(stripe.event("checkout.session.completed", session));
        assertEventually(() -> assertThat(api.balance(user, walletId)).isEqualTo("50.00"));
        providerBalance.synchronize();
        assertThat(transactionIds("POSTED")).contains(chargeId);
    }

    @Test
    void movementsTheApplicationDidNotCauseAreLeftForAPerson() throws Exception {
        String depositId = read(api.deposit(user, walletId, "40.00").andExpect(status().isAccepted()), "$.id");
        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/freeze", """
                {"reason": "Chargeback investigation"}""").andExpect(status().isOk());
        String session = stripe.sessionFor(depositId);
        stripe.pay(session);
        sendWebhook(stripe.event("checkout.session.completed", session));
        String paymentIntent = stripe.paymentIntentOf(session);
        assertEventually(() -> assertThat(stripe.refundedPaymentIntents()).contains(paymentIntent));

        // The deposit was rejected and refunded by the application; a dispute was not
        String refundId = stripe.addBalanceTransaction("refund", -4_000, 0, "mxn", paymentIntent);
        String disputeId = stripe.addBalanceTransaction("adjustment", -1_500, 1_500, "mxn", null);
        assertEventually(() -> {
            providerBalance.synchronize();
            assertThat(transactionIds("POSTED")).contains(refundId);
        });

        api.get(admin, TRANSACTIONS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].note".formatted(disputeId)).value("Not handled: adjustment"));
        api.get(user, TRANSACTIONS).andExpect(status().isForbidden());
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void aPersonClosesAnUnmatchedMovementWithoutPostingAnything() throws Exception {
        String disputeId = stripe.addBalanceTransaction("adjustment", -700, 0, "mxn", null);
        assertEventually(() -> {
            providerBalance.synchronize();
            assertThat(transactionIds("UNMATCHED")).contains(disputeId);
        });
        Map<String, Long> before = systemBalances("MXN");
        String resolve = TRANSACTIONS + "/" + providerBalance.providerName() + "/" + disputeId + "/resolve";
        String reason = """
                {"reason": "Fee agreed with the provider"}""";

        api.postJson(user, resolve, reason).andExpect(status().isForbidden());
        api.postJson(admin, resolve, """
                {"reason": ""}""").andExpect(status().isBadRequest());
        api.postJson(admin, TRANSACTIONS + "/" + providerBalance.providerName() + "/unknown/resolve", reason)
                .andExpect(status().isNotFound());

        api.postJson(admin, resolve, reason)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionReason").value("Fee agreed with the provider"))
                .andExpect(jsonPath("$.resolvedBy").isNotEmpty());
        api.postJson(admin, resolve, reason)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROVIDER_TRANSACTION_NOT_UNMATCHED"));

        assertThat(transactionIds("UNMATCHED")).doesNotContain(disputeId);
        assertThat(transactionIds("RESOLVED")).contains(disputeId);
        assertThat(systemBalances("MXN")).isEqualTo(before);

        String adminId = read(api.get(admin, "/api/v1/users/me"), "$.id");
        assertEventually(() -> assertThat(api.get(admin, "/api/v1/admin/audit-events?size=5&actorId=" + adminId
                        + "&action=PROVIDER_TRANSACTION_RESOLVED")
                .andReturn().getResponse().getContentAsString())
                .contains(disputeId, "reason: Fee agreed with the provider"));
    }

    @Test
    void withoutAStatusTheListShowsEveryStatus() throws Exception {
        String session = paidDeposit("10.00");
        String chargeId = stripe.addBalanceTransaction("charge", 1_000, 38, "mxn", stripe.paymentIntentOf(session));
        String disputeId = stripe.addBalanceTransaction("adjustment", -100, 0, "mxn", null);
        assertEventually(() -> {
            providerBalance.synchronize();
            assertThat(transactionIds("POSTED")).contains(chargeId);
        });

        String body = api.get(admin, TRANSACTIONS + "?limit=200")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).contains(chargeId, disputeId);
    }

    private String paidDeposit(String amount) throws Exception {
        String depositId = read(api.deposit(user, walletId, amount).andExpect(status().isAccepted()), "$.id");
        String session = stripe.sessionFor(depositId);
        stripe.pay(session);
        sendWebhook(stripe.event("checkout.session.completed", session));
        assertEventually(() -> assertThat(api.balance(user, walletId)).isEqualTo(amount));
        return session;
    }

    private void sendWebhook(String payload) throws Exception {
        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", signer.header(payload, Instant.now().getEpochSecond())))
                .andExpect(status().isOk());
    }

    private Map<String, Long> systemBalances(String currency) throws Exception {
        String body = api.get(admin, "/api/v1/admin/ledger/system-accounts")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> accounts = JsonPath.read(body, "$[?(@.currency == '%s')]".formatted(currency));
        Map<String, Long> balances = new HashMap<>();
        accounts.forEach(account -> balances.put((String) account.get("code"),
                ((Number) account.get("balanceMinor")).longValue()));
        return balances;
    }

    private List<String> transactionIds(String status) throws Exception {
        return JsonPath.read(api.get(admin, TRANSACTIONS + "?status=" + status + "&limit=200")
                .andReturn().getResponse().getContentAsString(), "$[*].id");
    }
}
