package com.irvinsegura.wallet.transfers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WalletDepositIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void newWalletStartsEmptyAndShowsDepositsInItsStatement() throws Exception {
        String token = newUserToken();
        String walletId = walletId(openWallet(token, "MXN")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/wallets/")))
                .andExpect(jsonPath("$.balance.amount").value("0.00"))
                .andExpect(jsonPath("$.balance.currency").value("MXN"))
                .andExpect(jsonPath("$.status").value("ACTIVE")));

        deposit(token, walletId, "100.50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.amount.amount").value("100.50"))
                .andExpect(jsonPath("$.balance.amount").value("100.50"));
        deposit(token, walletId, "20").andExpect(jsonPath("$.balance.amount").value("120.50"));

        mockMvc.perform(get("/api/v1/wallets/" + walletId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.amount").value("120.50"));
        mockMvc.perform(get("/api/v1/wallets/" + walletId + "/movements?size=1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[0].direction").value("IN"))
                .andExpect(jsonPath("$.items[0].amount.amount").value("20.00"))
                .andExpect(jsonPath("$.items[0].balanceAfter.amount").value("120.50"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void onlyOneWalletPerCurrencyAndOnlySupportedCurrencies() throws Exception {
        String token = newUserToken();
        openWallet(token, "MXN").andExpect(status().isCreated());
        openWallet(token, "USD").andExpect(status().isCreated());

        openWallet(token, "MXN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));
        openWallet(token, "EUR")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        openWallet(token, "mxn").andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/wallets").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void usersCannotSeeOrFundOtherUsersWallets() throws Exception {
        String owner = newUserToken();
        String stranger = newUserToken();
        String walletId = walletId(openWallet(owner, "MXN"));

        mockMvc.perform(get("/api/v1/wallets/" + walletId).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
        deposit(stranger, walletId, "10.00").andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidAmounts() throws Exception {
        String token = newUserToken();
        String walletId = walletId(openWallet(token, "MXN"));

        deposit(token, walletId, "-5.00").andExpect(status().isBadRequest());
        deposit(token, walletId, "0").andExpect(status().isBadRequest());
        // MXN has two decimals: rejected instead of silently rounded
        deposit(token, walletId, "10.505")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void concurrentDepositsAreAllCounted() throws Exception {
        String token = newUserToken();
        String walletId = walletId(openWallet(token, "MXN"));

        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<Callable<Integer>> deposits = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                deposits.add(() -> deposit(token, walletId, "10.00").andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : executor.invokeAll(deposits)) {
                assertThat(result.get()).isEqualTo(201);
            }
        }

        mockMvc.perform(get("/api/v1/wallets/" + walletId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.balance.amount").value("250.00"));
    }

    @Test
    void walletsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/wallets")).andExpect(status().isUnauthorized());
    }

    private ResultActions openWallet(String token, String currency) throws Exception {
        return mockMvc.perform(post("/api/v1/wallets").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currency": "%s"}""".formatted(currency)));
    }

    private ResultActions deposit(String token, String walletId, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/wallets/" + walletId + "/deposits")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}""".formatted(amount)));
    }

    private String newUserToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "fullName": "Ana López"}"""
                                .formatted(email, password)))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String walletId(ResultActions openWallet) throws Exception {
        return JsonPath.read(openWallet.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
