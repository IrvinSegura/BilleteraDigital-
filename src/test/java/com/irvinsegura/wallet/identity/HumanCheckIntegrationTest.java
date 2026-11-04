package com.irvinsegura.wallet.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.identity.domain.port.HumanVerifier;
import java.util.UUID;
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
 * The requests that email any address they are given need a CAPTCHA token. The verifier is a stand-in for Turnstile,
 * which TurnstileVerifierTest covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, HumanCheckIntegrationTest.OnlyHumans.class})
class HumanCheckIntegrationTest {

    private static final String HUMAN = "token-from-a-person";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signUpNeedsAValidToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        register(email, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
        register(email, "made-up")
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
        register(email, HUMAN).andExpect(status().isAccepted());
    }

    @Test
    void passwordResetRequestsNeedAValidToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s"}""".formatted(email)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
        mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "captchaToken": "%s"}""".formatted(email, HUMAN)))
                .andExpect(status().isAccepted());
    }

    private ResultActions register(String email, String captchaToken) throws Exception {
        String token = captchaToken == null ? "" : ", \"captchaToken\": \"%s\"".formatted(captchaToken);
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "correct horse battery staple", "fullName": "Ana López"%s}"""
                .formatted(email, token)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OnlyHumans {

        @Bean
        @Primary
        HumanVerifier onlyHumans() {
            return (token, remoteIp) -> HUMAN.equals(token);
        }
    }
}
