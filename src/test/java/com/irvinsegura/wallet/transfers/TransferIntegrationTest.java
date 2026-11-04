package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TransferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

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
    void transferMovesMoneyAndAppearsInBothStatements() throws Exception {
        String transferId = read(api.transfer(alice, aliceWallet, bob.email().toUpperCase(), "30.25", newKey())
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.recipientEmail").value(bob.email()))
                .andExpect(jsonPath("$.amount.amount").value("30.25"))
                .andExpect(jsonPath("$.description").value("Dinner")), "$.id");

        assertThat(api.balance(alice, aliceWallet)).isEqualTo("69.75");
        assertThat(api.balance(bob, bobWallet)).isEqualTo("30.25");
        api.get(alice, "/api/v1/wallets/" + aliceWallet + "/movements")
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.items[0].direction").value("OUT"))
                .andExpect(jsonPath("$.items[0].operationId").value(transferId));
        api.get(bob, "/api/v1/wallets/" + bobWallet + "/movements")
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].operationId").value(transferId));
    }

    @Test
    void bothSidesCanReadTheTransferAndNobodyElse() throws Exception {
        String transferId = read(api.transfer(alice, aliceWallet, bob.email(), "1.00", newKey()), "$.id");

        api.get(alice, "/api/v1/transfers/" + transferId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId))
                .andExpect(jsonPath("$.direction").value("SENT"))
                .andExpect(jsonPath("$.sourceWalletId").value(aliceWallet))
                .andExpect(jsonPath("$.senderEmail").value(alice.email()))
                .andExpect(jsonPath("$.recipientName").value("Ana López"));
        // The recipient sees who paid them, but not the sender's wallet
        api.get(bob, "/api/v1/transfers/" + transferId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("RECEIVED"))
                .andExpect(jsonPath("$.sourceWalletId").doesNotExist())
                .andExpect(jsonPath("$.senderName").value("Ana López"))
                .andExpect(jsonPath("$.description").value("Dinner"));
        api.get(api.newUser(), "/api/v1/transfers/" + transferId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    void recentRecipientsComeMostRecentlyPaidFirst() throws Exception {
        TestUser carol = api.newUser();
        api.openWalletId(carol, "MXN");
        api.transfer(alice, aliceWallet, bob.email(), "1.00", newKey()).andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, carol.email(), "1.00", newKey()).andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bob.email(), "2.00", newKey()).andExpect(status().isCreated());

        api.get(alice, "/api/v1/transfers/recipients")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value(bob.email()))
                .andExpect(jsonPath("$[0].displayName").value("Ana L."))
                .andExpect(jsonPath("$[0].timesSent").value(2))
                .andExpect(jsonPath("$[1].email").value(carol.email()));
        api.get(alice, "/api/v1/transfers/recipients?limit=1").andExpect(jsonPath("$.length()").value(1));
        api.get(bob, "/api/v1/transfers/recipients").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recipientLookupShowsAShortNameOnlyForWhoCanReceive() throws Exception {
        api.get(alice, "/api/v1/transfers/recipient?currency=MXN&email=" + bob.email().toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(bob.email()))
                .andExpect(jsonPath("$.displayName").value("Ana L."))
                .andExpect(jsonPath("$.timesSent").value(0))
                .andExpect(jsonPath("$.lastSentAt").doesNotExist());

        // No USD wallet, nobody with that email, or oneself: the same answer as the transfer would give
        api.get(alice, "/api/v1/transfers/recipient?currency=USD&email=" + bob.email())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
        api.get(alice, "/api/v1/transfers/recipient?currency=MXN&email=nobody-" + newKey() + "@example.com")
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
        api.get(alice, "/api/v1/transfers/recipient?currency=MXN&email=" + alice.email())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
        api.get(alice, "/api/v1/transfers/recipient?currency=mxn&email=" + bob.email())
                .andExpect(status().isBadRequest());
        api.get(alice, "/api/v1/transfers/recipient?currency=ZZZ&email=" + bob.email())
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryWithTheSameKeyReturnsTheOriginalTransfer() throws Exception {
        String key = newKey();
        String first = read(api.transfer(alice, aliceWallet, bob.email(), "10.00", key), "$.id");

        String retry = read(api.transfer(alice, aliceWallet, bob.email(), "10", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")), "$.id");

        assertThat(retry).isEqualTo(first);
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("90.00");
    }

    @Test
    void concurrentRetriesWithTheSameKeyMoveMoneyOnce() throws Exception {
        String key = newKey();

        List<MockHttpServletResponse> responses = runConcurrently(10,
                i -> api.transfer(alice, aliceWallet, bob.email(), "10.00", key).andReturn().getResponse());

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
        assertThat(responses).extracting(response -> response.getHeader("Location")).containsOnly(
                responses.getFirst().getHeader("Location"));
        assertThat(responses).filteredOn(response -> "false".equals(response.getHeader("Idempotent-Replayed")))
                .hasSize(1);
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("90.00");
        assertThat(api.balance(bob, bobWallet)).isEqualTo("10.00");
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        String key = newKey();
        api.transfer(alice, aliceWallet, bob.email(), "10.00", key).andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bob.email(), "11.00", key)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("90.00");
    }

    @Test
    void failedTransferDoesNotUseUpItsKey() throws Exception {
        String key = newKey();
        api.transfer(alice, aliceWallet, bob.email(), "150.00", key)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        api.deposit(alice, aliceWallet, "50.00").andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bob.email(), "150.00", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("0.00");
    }

    @Test
    void rejectsInvalidRecipients() throws Exception {
        TestUser dollarsOnly = api.newUser();
        api.openWalletId(dollarsOnly, "USD");

        api.transfer(alice, aliceWallet, alice.email(), "1.00", newKey())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));
        api.transfer(alice, aliceWallet, "nobody-" + newKey() + "@example.com", "1.00", newKey())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
        // Same error when the user exists but has no wallet in the sender's currency
        api.transfer(alice, aliceWallet, dollarsOnly.email(), "1.00", newKey())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("100.00");
    }

    @Test
    void aConceptWithMarkupIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", alice.bearer())
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceWalletId": "%s", "recipientEmail": "%s", "amount": 1.00,
                                 "description": "<img src=x onerror=alert(1)>", %s}"""
                                .formatted(aliceWallet, bob.email(), ApiClient.WITH_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("100.00");
    }

    @Test
    void cannotSendFromSomeoneElsesWallet() throws Exception {
        api.transfer(bob, aliceWallet, bob.email(), "1.00", newKey())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void requiresAnIdempotencyKey() throws Exception {
        api.transfer(alice, aliceWallet, bob.email(), "1.00", "")
                .andExpect(status().isBadRequest());
    }

    @Test
    void opposingConcurrentTransfersConserveMoney() throws Exception {
        api.deposit(bob, bobWallet, "100.00").andExpect(status().isCreated());

        // Half go A->B and half B->A at the same time: a lock-order bug would show up here as a deadlock (500)
        List<MockHttpServletResponse> responses = runConcurrently(40, i -> (i % 2 == 0
                ? api.transfer(alice, aliceWallet, bob.email(), "7.00", newKey())
                : api.transfer(bob, bobWallet, alice.email(), "7.00", newKey())).andReturn().getResponse());

        // 422 (insufficient funds) is fine depending on interleaving; anything else is a bug
        assertThat(responses).extracting(MockHttpServletResponse::getStatus).isSubsetOf(201, 422);
        BigDecimal total = new BigDecimal(api.balance(alice, aliceWallet))
                .add(new BigDecimal(api.balance(bob, bobWallet)));
        assertThat(total).isEqualByComparingTo("200.00");
    }

    private static <T> List<T> runConcurrently(int tasks, IndexedTask<T> task) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<Callable<T>> callables = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int index = i;
                callables.add(() -> task.run(index));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : executor.invokeAll(callables)) {
                results.add(future.get());
            }
            return results;
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws Exception;
    }
}
