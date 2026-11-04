package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.NO_STEP_UP;
import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.WITH_PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.newClabe;
import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.ApiClient.withCode;
import static com.irvinsegura.wallet.support.ApiClient.withPassword;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.identity.domain.model.Totp;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Money leaving a wallet needs proof that the session's holder is the owner (step-up): the two-factor code, or the
 * password for accounts without it. Transfers under the exemption (MXN 200 in the test profile) to someone paid before
 * are exempt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StepUpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private TestUser alice;
    private TestUser bob;
    private String aliceWallet;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        alice = api.newUser();
        bob = api.newUser();
        aliceWallet = api.openWalletId(alice, "MXN");
        api.openWalletId(bob, "MXN");
        api.deposit(alice, aliceWallet, "5000.00").andExpect(status().isCreated());
    }

    @Test
    void aFirstTransferToSomeoneNeedsThePassword() throws Exception {
        String key = newKey();
        api.transfer(alice, aliceWallet, bob.email(), "10.00", key, NO_STEP_UP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_REQUIRED"));
        api.transfer(alice, aliceWallet, bob.email(), "10.00", key, withPassword("not the password"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("5000.00");

        // The refused attempts never claimed the key, so the same request goes through once confirmed
        api.transfer(alice, aliceWallet, bob.email(), "10.00", key, WITH_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("4990.00");
    }

    @Test
    void smallTransfersToSomeonePaidBeforeAreExempt() throws Exception {
        api.transfer(alice, aliceWallet, bob.email(), "10.00", newKey(), WITH_PASSWORD).andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bob.email(), "199.99", newKey(), NO_STEP_UP).andExpect(status().isCreated());
        // The exemption ends at the threshold itself
        api.transfer(alice, aliceWallet, bob.email(), "200.00", newKey(), NO_STEP_UP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_REQUIRED"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("4790.01");
    }

    @Test
    void retryingATransferThatWentThroughNeedsNoNewProof() throws Exception {
        String key = newKey();
        api.transfer(alice, aliceWallet, bob.email(), "25.00", key, WITH_PASSWORD).andExpect(status().isCreated());

        api.transfer(alice, aliceWallet, bob.email(), "25.00", key, NO_STEP_UP)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("4975.00");
    }

    @Test
    void everyWithdrawalNeedsTheProof() throws Exception {
        String bankAccount = read(api.addBankAccount(alice, newClabe()).andExpect(status().isCreated()), "$.id");

        api.withdrawTo(alice, aliceWallet, "1.00", newKey(), bankAccount, NO_STEP_UP)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_REQUIRED"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("5000.00");

        api.withdrawTo(alice, aliceWallet, "1.00", newKey(), bankAccount, WITH_PASSWORD)
                .andExpect(status().is2xxSuccessful());
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("4999.00");
    }

    @Test
    void accountsWithTwoFactorGiveItsCodeAndEachCodeWorksOnce() throws Exception {
        String secret = read(api.postJson(alice, "/api/v1/users/me/two-factor", """
                {"currentPassword": "%s"}""".formatted(PASSWORD)), "$.secret");
        Instant now = Instant.now();
        api.postJson(alice, "/api/v1/users/me/two-factor/confirm", """
                {"code": "%s"}""".formatted(Totp.codeAt(secret, now))).andExpect(status().isOk());

        // With the app on, the password alone is not enough: a phished password must not skip the second factor
        api.transfer(alice, aliceWallet, bob.email(), "10.00", newKey(), WITH_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));

        String code = Totp.codeAt(secret, now.plusSeconds(30));
        api.transfer(alice, aliceWallet, bob.email(), "10.00", newKey(), withCode(code))
                .andExpect(status().isCreated());
        api.transfer(alice, aliceWallet, bob.email(), "1000.00", newKey(), withCode(code))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("4990.00");
    }

    @Test
    void wrongProofsLockTheAccountLikeWrongLogins() throws Exception {
        // wallet.identity.lockout.max-failed-attempts is 5
        for (int attempt = 0; attempt < 5; attempt++) {
            api.transfer(alice, aliceWallet, bob.email(), "10.00", newKey(), withPassword("guess-" + attempt))
                    .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        }

        api.transfer(alice, aliceWallet, bob.email(), "10.00", newKey(), WITH_PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(api.balance(alice, aliceWallet)).isEqualTo("5000.00");
    }
}
