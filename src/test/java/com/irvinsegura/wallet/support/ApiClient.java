package com.irvinsegura.wallet.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Small HTTP client for integration tests, so each test reads as the API calls it makes.
 */
public final class ApiClient {

    private static final String PASSWORD = "correct horse battery staple";

    private final MockMvc mockMvc;

    public ApiClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public TestUser newUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "fullName": "Ana López"}"""
                                .formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk());
        return new TestUser(email, read(login, "$.accessToken"));
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

    public record TestUser(String email, String token) {

        public String bearer() {
            return "Bearer " + token;
        }
    }
}
