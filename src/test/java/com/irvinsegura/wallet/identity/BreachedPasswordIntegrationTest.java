package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.RESET_PASSWORD_SUBJECT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.identity.domain.port.BreachedPasswords;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every way of setting a password refuses one listed in a breach. The list is a stand-in for Have I Been Pwned, which
 * PwnedPasswordsClientTest covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, BreachedPasswordIntegrationTest.KnownBreaches.class})
class BreachedPasswordIntegrationTest {

    // Long and uncommon enough for the local rules, so only the breach list can refuse it
    private static final String BREACHED = "listed in a breach somewhere";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, mailpit);
    }

    @Test
    void registrationRefusesABreachedPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "fullName": "Breach Test"}"""
                        .formatted("user-" + UUID.randomUUID() + "@example.com", BREACHED)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BREACHED_PASSWORD"));
    }

    @Test
    void changingToABreachedPasswordIsRefused() throws Exception {
        TestUser user = api.newUser();

        api.postJson(user, "/api/v1/users/me/password", """
                        {"currentPassword": "%s", "newPassword": "%s"}""".formatted(PASSWORD, BREACHED))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BREACHED_PASSWORD"));
        api.login(user.email(), PASSWORD);
    }

    @Test
    void aResetToABreachedPasswordKeepsTheLink() throws Exception {
        TestUser user = api.newUser();
        mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(user.email())))
                .andExpect(status().isAccepted());
        String token = api.awaitLinkToken(user.email(), RESET_PASSWORD_SUBJECT, 1);

        resetPassword(token, BREACHED)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BREACHED_PASSWORD"));
        resetPassword(token, "a fresh unlisted passphrase").andExpect(status().isNoContent());
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "newPassword": "%s"}""".formatted(token, newPassword)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KnownBreaches {

        @Bean
        @Primary
        BreachedPasswords knownBreaches() {
            return BREACHED::equals;
        }
    }
}
