package com.irvinsegura.wallet.payments;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.payments.application.StalePaymentService;
import com.irvinsegura.wallet.payments.infrastructure.stripe.StripeSignatureVerifier;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.FakeStripe;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Deposits with Stripe as the provider, against {@link FakeStripe}: the real HTTP client, the real webhook endpoint
 * with signatures, and the real database.
 */
@SpringBootTest(properties = {
        "wallet.payments.provider=stripe",
        "wallet.payments.stripe.api-key=sk_test_fake",
        "wallet.payments.stripe.webhook-secret=" + StripeDepositIntegrationTest.WEBHOOK_SECRET})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StripeDepositIntegrationTest.FakeStripeConfiguration.class})
class StripeDepositIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_test";
    private static final String WEBHOOK = "/api/v1/payments/webhooks/stripe";

    private final StripeSignatureVerifier signer =
            new StripeSignatureVerifier(WEBHOOK_SECRET, Duration.ofMinutes(5), Clock.systemUTC());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private FakeStripe stripe;

    @Autowired
    private StalePaymentService stalePaymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiClient api;
    private TestUser user;
    private String walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        user = api.newUser();
        walletId = api.openWalletId(user, "MXN");
    }

    @AfterEach
    void stripeIsBack() {
        stripe.setDown(false);
    }

    @Test
    void aDepositIsCreditedOnlyOnceStripeConfirmsThePayment() throws Exception {
        String key = newKey();
        String depositId = read(api.deposit(user, walletId, "100.50", key)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl", startsWith("https://checkout.stripe.test/")))
                .andExpect(jsonPath("$.checkoutExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.balance").value(nullValue())), "$.id");
        assertThat(api.balance(user, walletId)).isEqualTo("0.00");

        String session = stripe.sessionFor(depositId);
        stripe.pay(session);
        String completed = stripe.event("checkout.session.completed", session);
        sendWebhook(completed).andExpect(status().isOk());

        assertEventually(() -> deposit(depositId)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balance.amount").value("100.50"))
                .andExpect(jsonPath("$.checkoutUrl").value(nullValue())));
        // Stripe delivers events more than once
        sendWebhook(completed).andExpect(status().isOk());
        assertThat(api.balance(user, walletId)).isEqualTo("100.50");
        api.deposit(user, walletId, "100.50", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void webhooksWithoutAValidSignatureChangeNothing() throws Exception {
        String depositId = startDeposit("25.00");
        String session = stripe.sessionFor(depositId);
        stripe.pay(session);
        String completed = stripe.event("checkout.session.completed", session);

        // No access token is needed, but a signature is
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(completed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK"));
        String forged = new StripeSignatureVerifier("whsec_guess", Duration.ofMinutes(5), Clock.systemUTC())
                .header(completed, Instant.now().getEpochSecond());
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(completed)
                        .header("Stripe-Signature", forged))
                .andExpect(status().isBadRequest());

        deposit(depositId).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(api.balance(user, walletId)).isEqualTo("0.00");
    }

    @Test
    void moneyArrivingForAFrozenWalletIsRefunded() throws Exception {
        String depositId = startDeposit("40.00");
        api.postJson(api.admin(), "/api/v1/admin/wallets/" + walletId + "/freeze", """
                {"reason": "Chargeback investigation"}""").andExpect(status().isOk());

        String session = stripe.sessionFor(depositId);
        stripe.pay(session);
        sendWebhook(stripe.event("checkout.session.completed", session)).andExpect(status().isOk());

        assertEventually(() -> deposit(depositId)
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("WALLET_NOT_ACTIVE")));
        assertEventually(() -> assertThat(stripe.refundedPaymentIntents()).contains(stripe.paymentIntentOf(session)));
        assertEventually(() -> assertThat(notificationTypes()).contains("DEPOSIT_REJECTED"));
        assertThat(api.balance(user, walletId)).isEqualTo("0.00");
    }

    @Test
    void anExpiredCheckoutExpiresTheDeposit() throws Exception {
        String depositId = startDeposit("15.00");

        String session = stripe.sessionFor(depositId);
        stripe.expire(session);
        sendWebhook(stripe.event("checkout.session.expired", session)).andExpect(status().isOk());

        assertEventually(() -> deposit(depositId).andExpect(jsonPath("$.status").value("EXPIRED")));
    }

    @Test
    void paymentsWhoseWebhookNeverArrivesAreSettledByAskingStripe() throws Exception {
        String paidId = startDeposit("30.00");
        String abandonedId = startDeposit("70.00");
        stripe.pay(stripe.sessionFor(paidId));
        // Both checkouts expired a while ago and no webhook came
        jdbcTemplate.update("UPDATE payments SET expires_at = now() - interval '1 hour' WHERE id IN (?, ?)",
                UUID.fromString(paidId), UUID.fromString(abandonedId));

        assertThat(stalePaymentService.resolveStalePayments()).isGreaterThanOrEqualTo(2);

        assertThat(stripe.status(stripe.sessionFor(abandonedId))).isEqualTo("expired");
        assertEventually(() -> deposit(paidId).andExpect(jsonPath("$.status").value("COMPLETED")));
        assertEventually(() -> deposit(abandonedId).andExpect(jsonPath("$.status").value("EXPIRED")));
        assertThat(api.balance(user, walletId)).isEqualTo("30.00");
    }

    @Test
    void aDepositFailsWhenStripeCannotBeReached() throws Exception {
        stripe.setDown(true);
        String key = newKey();

        api.deposit(user, walletId, "10.00", key)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        // Nothing was charged; the key keeps pointing at the failed deposit, a new key makes a new attempt
        stripe.setDown(false);
        api.deposit(user, walletId, "10.00", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        api.deposit(user, walletId, "10.00", newKey()).andExpect(status().isAccepted());
    }

    private String startDeposit(String amount) throws Exception {
        return read(api.deposit(user, walletId, amount).andExpect(status().isAccepted()), "$.id");
    }

    private ResultActions deposit(String depositId) throws Exception {
        return api.get(user, "/api/v1/wallets/" + walletId + "/deposits/" + depositId).andExpect(status().isOk());
    }

    private ResultActions sendWebhook(String payload) throws Exception {
        return mockMvc.perform(post(WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("Stripe-Signature", signer.header(payload, Instant.now().getEpochSecond())));
    }

    private List<String> notificationTypes() throws Exception {
        return JsonPath.read(api.get(user, "/api/v1/notifications").andReturn().getResponse().getContentAsString(),
                "$.items[*].type");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStripeConfiguration {

        @Bean
        FakeStripe fakeStripe() {
            return new FakeStripe();
        }

        @Bean
        DynamicPropertyRegistrar stripeApiUrl(FakeStripe stripe) {
            return registry -> registry.add("wallet.payments.stripe.api-url", stripe::apiUrl);
        }
    }
}
