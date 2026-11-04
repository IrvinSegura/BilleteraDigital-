package com.irvinsegura.wallet.support;

import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Small HTTP client for integration tests, so each test reads as the API calls it makes.
 */
public final class ApiClient {

    public static final String PASSWORD = "correct horse battery staple";
    public static final String VERIFY_EMAIL_SUBJECT = "Confirm your email address";
    public static final String RESET_PASSWORD_SUBJECT = "Reset your password";
    public static final String SIGN_UP_ATTEMPTED_SUBJECT = "Someone tried to sign up with your email";
    // Step-up proof for money leaving a wallet: every test user's password (accounts without two-factor)
    public static final String WITH_PASSWORD = withPassword(PASSWORD);
    public static final String NO_STEP_UP = "";
    // Created at startup from application-test.yml
    private static final String ADMIN_EMAIL = "admin@wallet.test";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    private final MockMvc mockMvc;
    private final Mailbox mailbox;
    // The bank account each user's MXN withdrawals go to, added on their first withdrawal
    private final Map<String, String> bankAccounts = new ConcurrentHashMap<>();

    public ApiClient(MockMvc mockMvc, MailpitContainer mailpit) {
        this.mockMvc = mockMvc;
        this.mailbox = new Mailbox(mailpit);
    }

    /**
     * A signed-in user who confirmed their email through the emailed link, as every account must before signing in.
     */
    public TestUser newUser() throws Exception {
        String email = registerUnverified();
        confirmEmailOf(email);
        return login(email, PASSWORD);
    }

    /**
     * Signs up a new address without following the emailed link, so the account exists but cannot sign in yet.
     *
     * @return the address
     */
    public String registerUnverified() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        register(email).andExpect(status().isAccepted());
        return email;
    }

    public ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "Ana López"}""".formatted(email, PASSWORD)));
    }

    /**
     * Follows the newest verification link emailed to the address.
     */
    public void confirmEmailOf(String email) throws Exception {
        confirmEmail(awaitLinkToken(email, VERIFY_EMAIL_SUBJECT, 1)).andExpect(status().isNoContent());
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

    /**
     * MXN withdrawals go to a bank account of the user, added on their first one; others go without one.
     */
    public ResultActions withdraw(TestUser user, String walletId, String amount, String idempotencyKey)
            throws Exception {
        return withdrawTo(user, walletId, amount, idempotencyKey, defaultBankAccount(user, walletId));
    }

    public ResultActions withdrawTo(TestUser user, String walletId, String amount, String idempotencyKey,
            String bankAccountId) throws Exception {
        return withdrawTo(user, walletId, amount, idempotencyKey, bankAccountId, WITH_PASSWORD);
    }

    /**
     * @param stepUp the step-up fields as JSON members, e.g. {@link #WITH_PASSWORD} or {@link #withCode}; empty for
     *               none
     */
    public ResultActions withdrawTo(TestUser user, String walletId, String amount, String idempotencyKey,
            String bankAccountId, String stepUp) throws Exception {
        String bankAccount = bankAccountId == null ? "" : ", \"bankAccountId\": \"%s\"".formatted(bankAccountId);
        return mockMvc.perform(post("/api/v1/wallets/" + walletId + "/withdrawals")
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s%s%s}""".formatted(amount, bankAccount, members(stepUp))));
    }

    public ResultActions addBankAccount(TestUser user, String clabe) throws Exception {
        return postJson(user, "/api/v1/bank-accounts", """
                {"clabe": "%s", "holderName": "Ana López"}""".formatted(clabe));
    }

    /**
     * A random CLABE with a correct check digit, at bank 002.
     */
    public static String newClabe() {
        StringBuilder digits = new StringBuilder("002");
        for (int i = 0; i < 14; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        int[] weights = {3, 7, 1};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (digits.charAt(i) - '0') * weights[i % 3] % 10;
        }
        return digits.append((10 - sum % 10) % 10).toString();
    }

    // Only MXN wallets the user owns need one; for anything else, or a user who cannot add one (a disabled account),
    // the withdrawal goes without one and the API decides what to answer
    private String defaultBankAccount(TestUser user, String walletId) throws Exception {
        String wallet = get(user, "/api/v1/wallets/" + walletId).andReturn().getResponse().getContentAsString();
        if (!wallet.contains("\"MXN\"")) {
            return null;
        }
        synchronized (bankAccounts) {
            String existing = bankAccounts.get(user.email());
            if (existing != null) {
                return existing;
            }
            MvcResult added = addBankAccount(user, newClabe()).andReturn();
            if (added.getResponse().getStatus() != 201) {
                return null;
            }
            String id = JsonPath.read(added.getResponse().getContentAsString(), "$.id");
            bankAccounts.put(user.email(), id);
            return id;
        }
    }

    /**
     * Sends the user's password as step-up proof, which accounts without two-factor need for most transfers.
     */
    public ResultActions transfer(TestUser user, String sourceWalletId, String recipientEmail, String amount,
            String idempotencyKey) throws Exception {
        return transfer(user, sourceWalletId, recipientEmail, amount, idempotencyKey, WITH_PASSWORD);
    }

    /**
     * @param stepUp the step-up fields as JSON members, e.g. {@link #WITH_PASSWORD} or {@link #withCode}; empty for
     *               none
     */
    public ResultActions transfer(TestUser user, String sourceWalletId, String recipientEmail, String amount,
            String idempotencyKey, String stepUp) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                .header("Authorization", user.bearer())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceWalletId": "%s", "recipientEmail": "%s", "amount": %s, "description": "Dinner"%s}"""
                        .formatted(sourceWalletId, recipientEmail, amount, members(stepUp))));
    }

    public static String withCode(String code) {
        return "\"twoFactorCode\": \"%s\"".formatted(code);
    }

    public static String withPassword(String password) {
        return "\"currentPassword\": \"%s\"".formatted(password);
    }

    private static String members(String json) {
        return json.isEmpty() ? "" : ", " + json;
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
