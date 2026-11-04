package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * What a web app on another origin sees: CORS for its origin only, and a refresh token it never gets to read.
 */
@SpringBootTest(properties = "wallet.web.cors.allowed-origins=https://app.wallet.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BrowserSessionIntegrationTest {

    private static final String APP = "https://app.wallet.test";
    private static final String COOKIE = "wallet_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        user = new ApiClient(mockMvc, mailpit).newUser();
    }

    @Test
    void theRefreshTokenStaysInAnHttpOnlyCookie() throws Exception {
        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Session-Mode", "cookie")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(user.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        String setCookie = login.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Strict",
                "Path=/api/v1/auth");
        Cookie session = login.getCookie(COOKIE);

        // Without the header the cookie is ignored, so a forged cross-site form cannot use it
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());

        MockHttpServletResponse refreshed = refresh(session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        Cookie rotated = refreshed.getCookie(COOKIE);
        assertThat(rotated.getValue()).isNotEqualTo(session.getValue());

        mockMvc.perform(post("/api/v1/auth/logout").header("X-Session-Mode", "cookie").cookie(rotated))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        refresh(rotated).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheConfiguredOriginPassesCors() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, APP)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type, x-session-mode"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, APP))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private ResultActions refresh(Cookie session) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").header("X-Session-Mode", "cookie").cookie(session));
    }
}
