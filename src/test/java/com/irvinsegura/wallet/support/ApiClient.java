package com.irvinsegura.wallet.support;

import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Small HTTP client for integration tests, so each test reads as the API calls it makes.
 */
public final class ApiClient {

    public static final String PASSWORD = "correct horse battery staple";
    public static final String VERIFY_EMAIL_SUBJECT = "Confirm your email address";
    public static final String RESET_PASSWORD_SUBJECT = "Reset your password";
    // Created at startup from application-test.yml
    private static final String ADMIN_EMAIL = "admin@wallet.test";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    private final MockMvc mockMvc;
    private final Mailbox mailbox;

    public ApiClient(MockMvc mockMvc, MailpitContainer mailpit) {
        this.mockMvc = mockMvc;
        this.mailbox = new Mailbox(mailpit);
    }

    /**
     * A signed-in user who confirmed their email through the emailed link, so they can move money.
     */
    public TestUser newUser() throws Exception {
        TestUser user = newUnverifiedUser();
        verifyEmail(user);
        return user;
    }

    /**
     * A signed-in user who has not confirmed their email: they can receive money but not send or withdraw it.
     */
    public TestUser newUnverifiedUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "fullName": "Ana López"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return login(email, PASSWORD);
    }

    public void verifyEmail(TestUser user) throws Exception {
        confirmEmail(awaitLinkToken(user.email(), VERIFY_EMAIL_SUBJECT, 1)).andExpect(status().isNoContent());
    }

    public ResultActions confirmEmail(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s"}""".formatted(token)));
    }

    /**
     * Waits until at least {@code count} emails with that subject reached the address and returns the newest link's
     * token. Emails are sent by event listeners after commit, so they arrive a moment after the request.
     */
    public String awaitLinkToken(String email, String subject, int count) throws Exception {
        AtomicReference<String> newest = new AtomicReference<>();
        assertEventually(() -> {
            List<String> tokens = mailbox.linkTokensFor(email, subject);
            assertThat(tokens).hasSizeGreaterThanOrEqualTo(count);
            newest.set(tokens.getFirst());
        });
        return newest.get();
    }

    public Mailbox mailbox() {
        return mailbox;
    }

    public TestUser admin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    public TestUser login(String email, String password) throws Exception {
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, password)))
                .andExpect(status().isOk());
        return new TestUser(email, read(login, "$.accessToken"), read(login, "$.refreshToken"));
    }

    public ResultActions postJson(TestUser user, String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    public ResultActions openWallet(TestUser user, String currency) throws Exception {
        return mockMvc.perform(post("/api/v1/wallets").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "%s"}""".formatted(currency)));
    }

    public String openWalletId(TestUser user, String currency) throws Exception {
        return read(openWallet(user, currency).andExpect(status().isCreated()), "$.id");
    }

    public ResultActions deposit(TestUser user, String walletId, String amount) throws Exception {
        return deposit(user, walletId, amount, newKey());
    }

    public ResultActions deposit(TestUser user, String walletId, String amount, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/wallets/" + walletId + "/deposits")
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}""".formatted(amount)));
    }

    public ResultActions withdraw(TestUser user, String walletId, String amount) throws Exception {
        return withdraw(user, walletId, amount, newKey());
    }

    public ResultActions withdraw(TestUser user, String walletId, String amount, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/wallets/" + walletId + "/withdrawals")
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}""".formatted(amount)));
    }

    public ResultActions transfer(TestUser user, String sourceWalletId, String recipientEmail, String amount,
            String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceWalletId": "%s", "recipientEmail": "%s", "amount": %s, "description": "Dinner"}"""
                        .formatted(sourceWalletId, recipientEmail, amount)));
    }

    public ResultActions get(TestUser user, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", user.bearer()));
    }

    public String balance(TestUser user, String walletId) throws Exception {
        return read(get(user, "/api/v1/wallets/" + walletId).andExpect(status().isOk()), "$.balance.amount");
    }

    public static String read(ResultActions result, String jsonPath) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    public record TestUser(String email, String token, String refreshToken) {

        public String bearer() {
            return "Bearer " + token;
        }
    }
}
