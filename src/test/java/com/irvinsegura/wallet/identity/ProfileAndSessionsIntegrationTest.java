package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProfileAndSessionsIntegrationTest {

    private static final String ME = "/api/v1/users/me";
    private static final String FIREFOX = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0";

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
    void emailsAreWrittenInTheLanguageOfTheApp() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es-MX,es;q=0.9,en;q=0.8")
                        .content("""
                                {"email": "%s", "password": "%s", "fullName": "Ana López"}"""
                                .formatted(email, ApiClient.PASSWORD)))
                .andExpect(status().isAccepted());

        api.confirmEmail(api.awaitLinkToken(email, "Confirma tu correo electrónico", 1))
                .andExpect(status().isNoContent());
        assertEventually(() -> assertThat(api.mailbox().subjectsFor(email))
                .contains("Te damos la bienvenida a Billetera Digital"));
        TestUser user = api.login(email, ApiClient.PASSWORD);
        api.get(user, ME).andExpect(jsonPath("$.language").value("es"));

        // Switching the app's language changes the next emails...
        changeLanguage(user, "en").andExpect(status().isOk()).andExpect(jsonPath("$.language").value("en"));
        requestReset(email, null).andExpect(status().isAccepted());
        api.awaitLinkToken(email, ApiClient.RESET_PASSWORD_SUBJECT, 1);

        // ...and an app showing another language before signing in gets that one, without changing the account's
        requestReset(email, "es").andExpect(status().isAccepted());
        api.awaitLinkToken(email, "Restablece tu contraseña", 1);
        api.get(user, ME).andExpect(jsonPath("$.language").value("en"));

        changeLanguage(user, "fr").andExpect(status().isBadRequest());
    }

    @Test
    void usersChangeTheirNameAndPhone() throws Exception {
        TestUser user = api.newUser();

        updateProfile(user, """
                {"fullName": "  Ana   María López ", "phone": "+52 55 1234-5678"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana María López"))
                .andExpect(jsonPath("$.phone").value("+525512345678"))
                .andExpect(jsonPath("$.email").value(user.email()));
        api.get(user, ME).andExpect(jsonPath("$.phone").value("+525512345678"));

        // A field left out keeps its value; an empty phone removes it
        updateProfile(user, """
                {"phone": ""}""")
                .andExpect(jsonPath("$.fullName").value("Ana María López"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void anInvalidProfileIsRefused() throws Exception {
        TestUser user = api.newUser();

        updateProfile(user, """
                {"phone": "5512345678"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE"));
        updateProfile(user, """
                {"fullName": "   "}""")
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE"));
        updateProfile(user, """
                {"fullName": "%s"}""".formatted("a".repeat(101)))
                .andExpect(status().isBadRequest());
        // It shows up in other users' notifications and screens
        updateProfile(user, """
                {"fullName": "<script>alert(1)</script>Ana"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.get(user, ME).andExpect(jsonPath("$.fullName").value("Ana López"));
    }

    @Test
    void usersSeeTheirSessionsAndEndTheOnesTheyChoose() throws Exception {
        TestUser user = api.newUser();
        TestUser laptop = loginFrom(user.email(), FIREFOX);

        api.get(laptop, ME + "/sessions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].userAgent").value(FIREFOX))
                .andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$[1].current").value(false));
        String phoneSession = read(api.get(laptop, ME + "/sessions"), "$[1].id");

        mockMvc.perform(delete(ME + "/sessions/" + phoneSession).header(HttpHeaders.AUTHORIZATION, bearer(laptop)))
                .andExpect(status().isNoContent());
        // The ended session can't renew its access token any more
        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        api.get(laptop, ME + "/sessions").andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(delete(ME + "/sessions/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(laptop)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void endingTheOtherSessionsKeepsTheCurrentOne() throws Exception {
        TestUser user = api.newUser();
        TestUser second = api.login(user.email(), ApiClient.PASSWORD);
        TestUser third = api.login(user.email(), ApiClient.PASSWORD);

        mockMvc.perform(delete(ME + "/sessions").header(HttpHeaders.AUTHORIZATION, bearer(third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(2));
        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        refresh(second.refreshToken()).andExpect(status().isUnauthorized());
        refresh(third.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void nobodyEndsSomeoneElsesSession() throws Exception {
        TestUser owner = api.newUser();
        TestUser stranger = api.newUser();
        String ownerSession = read(api.get(owner, ME + "/sessions"), "$[0].id");

        mockMvc.perform(delete(ME + "/sessions/" + ownerSession).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound());
        refresh(owner.refreshToken()).andExpect(status().isOk());
    }

    private ResultActions changeLanguage(TestUser user, String language) throws Exception {
        return mockMvc.perform(put(ME + "/language").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"language": "%s"}""".formatted(language)));
    }

    private ResultActions requestReset(String email, String language) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s"}""".formatted(email));
        return mockMvc.perform(language == null ? request : request.header(HttpHeaders.ACCEPT_LANGUAGE, language));
    }

    private ResultActions updateProfile(TestUser user, String json) throws Exception {
        return mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private TestUser loginFrom(String email, String userAgent) throws Exception {
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login").header(HttpHeaders.USER_AGENT, userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(email, ApiClient.PASSWORD)))
                .andExpect(status().isOk());
        return new TestUser(email, read(login, "$.accessToken"), read(login, "$.refreshToken"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}""".formatted(refreshToken)));
    }

    private static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }
}
