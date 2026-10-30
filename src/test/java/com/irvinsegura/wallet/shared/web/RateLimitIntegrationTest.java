package com.irvinsegura.wallet.shared.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full stack: the filter must run after authentication to key buckets by user, and its 429 must look like every
 * other error. Rules here replace the default list, so registration and login are not limited.
 */
@SpringBootTest(properties = {
        "wallet.rate-limit.enabled=true",
        "wallet.rate-limit.rules[0].name=money-movement",
        "wallet.rate-limit.rules[0].methods[0]=POST",
        "wallet.rate-limit.rules[0].paths[0]=/api/v1/wallets/*/deposits",
        "wallet.rate-limit.rules[0].key=user",
        "wallet.rate-limit.rules[0].capacity=2",
        "wallet.rate-limit.rules[0].period=1h"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void depositsBeyondTheLimitAreRejectedPerUserWithoutMovingMoney() throws Exception {
        ApiClient api = new ApiClient(mockMvc);
        TestUser alice = api.newUser();
        TestUser bob = api.newUser();
        String aliceWallet = api.openWalletId(alice, "MXN");
        String bobWallet = api.openWalletId(bob, "MXN");

        api.deposit(alice, aliceWallet, "10.00").andExpect(status().isCreated())
                .andExpect(header().string(RateLimitFilter.REMAINING_HEADER, "1"));
        api.deposit(alice, aliceWallet, "10.00").andExpect(status().isCreated());
        api.deposit(alice, aliceWallet, "10.00")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string(RateLimitFilter.REMAINING_HEADER, "0"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        Assertions.assertThat(api.balance(alice, aliceWallet)).isEqualTo("20.00");
        // Bob shares the test's address but has his own bucket
        api.deposit(bob, bobWallet, "10.00").andExpect(status().isCreated());
    }
}
