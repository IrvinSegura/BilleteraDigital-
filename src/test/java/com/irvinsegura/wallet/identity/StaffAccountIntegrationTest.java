package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sign-up only ever makes customers; staff accounts are created by an administrator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StaffAccountIntegrationTest {

    private static final String USERS = "/api/v1/admin/users";
    private static final String ACCOUNT_DATA = "Tus datos de acceso a Billetera Digital";
    private static final Pattern PASSWORD = Pattern.compile("Contraseña: ((?:[a-zA-Z2-9]{4}-){3}[a-zA-Z2-9]{4})");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountTokens accountTokens;

    @Autowired
    private PasswordHasher passwordHasher;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, mailpit);
    }

    @Test
    void signUpAlwaysMakesACustomerWhateverTheRequestSays() throws Exception {
        String email = "role-" + UUID.randomUUID() + "@wallet.test";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en").content("""
                        {"email": "%s", "password": "%s", "fullName": "Mala Intención", "role": "ADMIN"}"""
                        .formatted(email, ApiClient.PASSWORD)))
                .andExpect(status().is2xxSuccessful());
        api.confirmEmailOf(email);

        api.get(api.login(email, ApiClient.PASSWORD), "/api/v1/users/me").andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void anAgentGetsTheirRoleAddressAndPasswordByEmail() throws Exception {
        TestUser admin = api.admin();
        String email = "agent-" + UUID.randomUUID() + "@wallet.test";

        var created = api.postJson(admin, USERS, """
                        {"email": "%s", "fullName": "Laura Soporte", "role": "SUPPORT", "reason": "New hire"}"""
                        .formatted(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPPORT"))
                .andExpect(jsonPath("$.emailVerified").value(true));
        String id = read(created, "$.id");

        String text = awaitAccountData(email);
        assertThat(text).contains("Rol: Soporte", "Correo: " + email);
        String password = passwordIn(text);

        // Nobody but the owner sees it: not in what the API answers, nor in the events kept in the outbox
        assertThat(created.andReturn().getResponse().getContentAsString()).doesNotContain(password);
        assertThat(api.get(admin, USERS + "/" + id).andReturn().getResponse().getContentAsString())
                .doesNotContain(password);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
                Long.class, "%" + password + "%")).isZero();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email))
                .doesNotContain(password);

        assertEventually(() -> assertThat(api.get(admin, "/api/v1/admin/audit-events?targetId=" + id)
                .andReturn().getResponse().getContentAsString()).contains("STAFF_ACCOUNT_CREATED"));

        // That password opens nothing but the way to replace it
        TestUser agent = api.login(email, password);
        api.get(agent, "/api/v1/users/me").andExpect(jsonPath("$.mustChangePassword").value(true));
        api.get(agent, "/api/v1/support/agent/summary")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        api.get(agent, "/api/v1/notifications").andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        // Not by sending the same one again
        api.postJson(agent, "/api/v1/users/me/password", """
                {"currentPassword": "%s", "newPassword": "%s"}""".formatted(password, password))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));

        var changed = api.postJson(agent, "/api/v1/users/me/password", """
                {"currentPassword": "%s", "newPassword": "Agent-Strong-Passw0rd!"}""".formatted(password))
                .andExpect(status().is2xxSuccessful());

        // The new session is free, and the emailed password no longer opens anything
        TestUser free = new TestUser(email, read(changed, "$.accessToken"), read(changed, "$.refreshToken"));
        api.get(free, "/api/v1/users/me").andExpect(jsonPath("$.mustChangePassword").value(false));
        api.get(free, "/api/v1/support/agent/summary").andExpect(status().isOk());
        api.openWallet(free, "MXN").andExpect(status().isForbidden());
        api.login(email, "Agent-Strong-Passw0rd!");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}""".formatted(email, password)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingTheSessionDoesNotGetAroundTheChange() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@wallet.test";
        api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Rita Soporte", "role": "SUPPORT", "reason": "New hire"}""".formatted(email))
                .andExpect(status().isCreated());
        TestUser agent = api.login(email, passwordIn(awaitAccountData(email)));

        var refreshed = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                {"refreshToken": "%s"}""".formatted(agent.refreshToken())))
                .andExpect(status().isOk());

        api.get(new TestUser(email, read(refreshed, "$.accessToken"), read(refreshed, "$.refreshToken")),
                "/api/v1/support/agent/summary").andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void anAgentWithTheEmailedPasswordCanStillLeave() throws Exception {
        String email = "leave-" + UUID.randomUUID() + "@wallet.test";
        api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Luz Soporte", "role": "SUPPORT", "reason": "New hire"}""".formatted(email))
                .andExpect(status().isCreated());
        TestUser agent = api.login(email, passwordIn(awaitAccountData(email)));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, agent.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"refreshToken": "%s"}""".formatted(agent.refreshToken())))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void aNewAdministratorChangesThePasswordBeforeAnythingElse() throws Exception {
        String email = "first-admin-" + UUID.randomUUID() + "@wallet.test";
        api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Nora Admin", "role": "ADMIN", "reason": "New hire"}""".formatted(email))
                .andExpect(status().isCreated());
        TestUser admin = api.login(email, passwordIn(awaitAccountData(email)));

        api.get(admin, USERS).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void nobodyElseHasToChangeTheirPassword() throws Exception {
        TestUser customer = api.newUser();

        api.get(customer, "/api/v1/users/me").andExpect(jsonPath("$.mustChangePassword").value(false));
        api.get(customer, "/api/v1/notifications").andExpect(status().isOk());
    }

    @Test
    void anAgentWhoResetsTheirPasswordNoLongerHasToChangeIt() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@wallet.test";
        api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Sara Soporte", "role": "SUPPORT", "reason": "New hire"}""".formatted(email))
                .andExpect(status().isCreated());
        awaitAccountData(email);

        mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(email)))
                .andExpect(status().is2xxSuccessful());
        String token = api.awaitLinkToken(email, "Restablece tu contraseña", 1);
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "Agent-Strong-Passw0rd!"}""".formatted(token)))
                .andExpect(status().is2xxSuccessful());

        TestUser agent = api.login(email, "Agent-Strong-Passw0rd!");
        api.get(agent, "/api/v1/support/agent/summary").andExpect(status().isOk());
    }

    @Test
    void theEmailSaysAdministratorForAnAdministrator() throws Exception {
        String email = "new-admin-" + UUID.randomUUID() + "@wallet.test";

        api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Nora Admin", "role": "ADMIN", "reason": "New hire"}""".formatted(email))
                .andExpect(status().isCreated());

        assertThat(awaitAccountData(email)).contains("Rol: Administrador");
    }

    @Test
    void anEventDeliveredTwiceDoesNotReplaceThePassword() throws Exception {
        String email = "twice-" + UUID.randomUUID() + "@wallet.test";
        String id = read(api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Marta Soporte", "role": "SUPPORT", "reason": "New hire"}"""
                .formatted(email)).andExpect(status().isCreated()), "$.id");
        String password = passwordIn(awaitAccountData(email));

        assertThat(accountTokens.issueInitialPassword(UUID.fromString(id))).isEmpty();

        api.login(email, password);
    }

    @Test
    void anAccountWithoutAPasswordYetMatchesNone() {
        assertThat(passwordHasher.matches(PasswordHasher.NO_PASSWORD, PasswordHasher.NO_PASSWORD)).isFalse();
    }

    @Test
    void anAgentWhoLostTheEmailCanStillResetTheirPassword() throws Exception {
        String email = "lost-" + UUID.randomUUID() + "@wallet.test";
        api.postJson(api.admin(), USERS, """
                {"email": "%s", "fullName": "Pablo Soporte", "role": "SUPPORT", "reason": "New hire"}"""
                .formatted(email)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(email)))
                .andExpect(status().is2xxSuccessful());
        String token = api.awaitLinkToken(email, "Restablece tu contraseña", 1);
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "newPassword": "Agent-Strong-Passw0rd!"}""".formatted(token)))
                .andExpect(status().is2xxSuccessful());

        api.login(email, "Agent-Strong-Passw0rd!");
    }

    @Test
    void staffAccountsAreAdministratorsOrAgentsOnly() throws Exception {
        TestUser admin = api.admin();

        api.postJson(admin, USERS, """
                {"email": "x-%s@wallet.test", "fullName": "Cliente", "role": "USER", "reason": "no"}"""
                .formatted(UUID.randomUUID())).andExpect(status().isBadRequest());
        api.postJson(admin, USERS, """
                {"email": "admin@wallet.test", "fullName": "Otro", "role": "ADMIN", "reason": "dup"}""")
                .andExpect(status().isConflict());
        api.postJson(api.newUser(), USERS, """
                {"email": "y-%s@wallet.test", "fullName": "Yo", "role": "ADMIN", "reason": "me"}"""
                .formatted(UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test
    void aCustomerWithMoneyCannotBecomeAnAgent() throws Exception {
        TestUser admin = api.admin();
        TestUser customer = api.newUser();
        String walletId = api.openWalletId(customer, "MXN");
        api.deposit(customer, walletId, "10.00").andExpect(status().isCreated());
        String userId = read(api.get(customer, "/api/v1/users/me"), "$.id");

        api.postJson(admin, USERS + "/" + userId + "/role", """
                {"role": "SUPPORT", "reason": "Hiring"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROLE_CHANGE_BLOCKED"))
                .andExpect(jsonPath("$.reason").value("BALANCE"));
    }

    private String awaitAccountData(String email) throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        assertEventually(() -> {
            List<String> texts = api.mailbox().textsFor(email, ACCOUNT_DATA);
            assertThat(texts).hasSize(1);
            text.set(texts.getFirst());
        });
        return text.get();
    }

    private static String passwordIn(String text) {
        Matcher matcher = PASSWORD.matcher(text);
        assertThat(matcher.find()).as("password in the email").isTrue();
        return matcher.group(1);
    }
}
