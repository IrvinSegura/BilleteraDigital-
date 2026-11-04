package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.identity.domain.model.Totp;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The administrator endpoints with two-factor authentication required, as outside the test profile.
 */
@SpringBootTest(properties = "wallet.identity.admin-requires-two-factor=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminTwoFactorIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@wallet.test";
    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String ADMIN_ENDPOINT = "/api/v1/admin/ledger/system-accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Test
    void administratorsNeedTwoFactorToUseAdminEndpoints() throws Exception {
        ApiClient api = new ApiClient(mockMvc, mailpit);
        TestUser admin = api.admin();

        api.get(admin, ADMIN_ENDPOINT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_TWO_FACTOR_REQUIRED"));
        // Their own profile still works, so they can set it up
        String secret = read(api.postJson(admin, "/api/v1/users/me/two-factor", """
                {"currentPassword": "%s"}""".formatted(ADMIN_PASSWORD)).andExpect(status().isCreated()), "$.secret");
        Instant now = Instant.now();
        api.postJson(admin, "/api/v1/users/me/two-factor/confirm", """
                {"code": "%s"}""".formatted(Totp.codeAt(secret, now))).andExpect(status().isOk());

        // The access token from before still lacks it; a new login proves it
        api.get(admin, ADMIN_ENDPOINT).andExpect(status().isForbidden());
        String accessToken = read(mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "twoFactorCode": "%s"}"""
                        .formatted(ADMIN_EMAIL, ADMIN_PASSWORD, Totp.codeAt(secret, now.plusSeconds(30)))))
                .andExpect(status().isOk()), "$.accessToken");
        api.get(new TestUser(ADMIN_EMAIL, accessToken, null), ADMIN_ENDPOINT).andExpect(status().isOk());
    }
}
