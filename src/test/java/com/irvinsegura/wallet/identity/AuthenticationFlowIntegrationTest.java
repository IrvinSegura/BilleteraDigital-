package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class AuthenticationFlowIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

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
    void registeredUserCanLogInOnceTheEmailIsVerified() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        // The password is right, but whoever signed up may not own the address
        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        api.confirmEmailOf(email);

        String body = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + read(body, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void signingUpWithATakenEmailLooksTheSameAndOnlyTellsItsOwner() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        // Normalized, so the same address in capitals is the same account
        register(email.toUpperCase(), "another long passphrase")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertEventually(() -> assertThat(api.mailbox().subjectsFor(email))
                .contains(ApiClient.SIGN_UP_ATTEMPTED_SUBJECT));
        // Nothing changed: the first password still signs in, the second does not
        login(email, PASSWORD).andExpect(status().isOk());
        login(email, "another long passphrase").andExpect(status().isUnauthorized());
    }

    @Test
    void signingUpAgainBeforeVerifyingSendsANewLink() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isAccepted());
        String first = api.awaitLinkToken(email, ApiClient.VERIFY_EMAIL_SUBJECT, 1);

        register(email, PASSWORD).andExpect(status().isAccepted());

        String second = api.awaitLinkToken(email, ApiClient.VERIFY_EMAIL_SUBJECT, 2);
        assertThat(second).isNotEqualTo(first);
        assertThat(api.mailbox().subjectsFor(email)).doesNotContain(ApiClient.SIGN_UP_ATTEMPTED_SUBJECT);
    }

    @Test
    void anUnverifiedAccountCanAskForANewLinkWithItsPassword() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isAccepted());
        api.awaitLinkToken(email, ApiClient.VERIFY_EMAIL_SUBJECT, 1);

        // Asking needs the right password, so nobody else can send links to the address this way
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "wrong password 123", "resendVerification": true}"""
                        .formatted(email)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "resendVerification": true}""".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        api.confirmEmail(api.awaitLinkToken(email, ApiClient.VERIFY_EMAIL_SUBJECT, 2)).andExpect(status().isNoContent());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        register(uniqueEmail(), "short")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        register(uniqueEmail(), "P@ssw0rd2024!")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMON_PASSWORD"));
    }

    @Test
    void unknownEmailAndWrongPasswordGetTheSameError() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        login(email, "wrong password 123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountLocksAfterFiveFailedLoginsEvenForTheRightPassword() throws Exception {
        String email = uniqueEmail();
        signUp(email);

        for (int i = 0; i < 5; i++) {
            login(email, "wrong password 123").andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesTheTokenAndReuseRevokesTheWholeSession() throws Exception {
        String email = uniqueEmail();
        signUp(email);
        String first = read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        String rotated = read(refresh(first)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");
        assertThat(rotated).isNotEqualTo(first);

        // Replaying the old token looks like theft: it fails and also kills the token issued in its place
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(rotated).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String email = uniqueEmail();
        signUp(email);
        String refreshToken =
                read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshTokenJson(refreshToken)))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        signUp(email);
        String accessToken =
                read(login(email, PASSWORD).andReturn().getResponse().getContentAsString(), "$.accessToken");
        String[] parts = accessToken.split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // Signed up and verified, so the account can sign in
    private void signUp(String email) throws Exception {
        register(email, PASSWORD).andExpect(status().isAccepted());
        api.confirmEmailOf(email);
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "Ana López"}""".formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refreshTokenJson(refreshToken)));
    }

    private static String refreshTokenJson(String refreshToken) {
        return """
                {"refreshToken": "%s"}""".formatted(refreshToken);
    }

    private static String read(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
