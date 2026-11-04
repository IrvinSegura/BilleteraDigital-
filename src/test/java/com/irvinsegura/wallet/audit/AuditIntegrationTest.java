package com.irvinsegura.wallet.audit;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiClient api;
    private TestUser admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
    }

    @Test
    void walletHistoryIsAudited() throws Exception {
        TestUser owner = api.newUser();
        String walletId = api.openWalletId(owner, "MXN");
        api.deposit(owner, walletId, "100.00").andExpect(status().isCreated());
        api.withdraw(owner, walletId, "25.00").andExpect(status().isCreated());
        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/freeze", """
                {"reason": "Chargeback investigation"}""").andExpect(status().isOk());

        // Listeners run after commit, asynchronously
        assertEventually(() -> assertThat(actionsFor(walletId)).containsExactly(
                "WALLET_FROZEN", "WITHDRAWAL_COMPLETED", "WITHDRAWAL_REQUESTED", "DEPOSIT_COMPLETED",
                "WALLET_OPENED"));

        String body = api.get(admin, "/api/v1/admin/audit-events?targetId=" + walletId + "&size=1")
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.items[0].details")).contains("Chargeback investigation");
        assertThat((String) JsonPath.read(body, "$.items[0].actorId"))
                .isEqualTo(read(api.get(admin, "/api/v1/users/me"), "$.id"));
        assertThat((Boolean) JsonPath.read(body, "$.hasNext")).isTrue();
    }

    @Test
    void lockoutsAreAuditedWithoutAnActor() throws Exception {
        TestUser user = api.newUser();
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {"email": "%s", "password": "wrong password 123"}""".formatted(user.email())));
        }

        assertEventually(() -> assertThat(actionsFor(userId))
                .containsExactly("USER_LOCKED_OUT", "USER_SIGNED_IN", "USER_EMAIL_VERIFIED", "USER_REGISTERED"));
        String body = api.get(admin, "/api/v1/admin/audit-events?targetId=" + userId)
                .andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(body, "$.items[0].actorId")).isNull();
    }

    @Test
    void theLogFiltersByActionAndMoment() throws Exception {
        TestUser user = api.newUser();
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");
        assertEventually(() -> assertThat(actionsFor(userId)).contains("USER_SIGNED_IN", "USER_REGISTERED"));
        String anHourAgo = Instant.now().minus(Duration.ofHours(1)).toString();
        String inAnHour = Instant.now().plus(Duration.ofHours(1)).toString();

        assertThat(actionsMatching(userId, "&action=USER_REGISTERED")).containsExactly("USER_REGISTERED");
        assertThat(actionsMatching(userId, "&action=WALLET_OPENED")).isEmpty();
        assertThat(actionsMatching(userId, "&from=" + anHourAgo + "&to=" + inAnHour)).contains("USER_REGISTERED");
        assertThat(actionsMatching(userId, "&from=" + inAnHour)).isEmpty();
        assertThat(actionsMatching(userId, "&to=" + anHourAgo)).isEmpty();
        api.get(admin, "/api/v1/admin/audit-events?from=yesterday").andExpect(status().isBadRequest());
    }

    @Test
    void auditLogIsAppendOnly() throws Exception {
        TestUser user = api.newUser();
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");
        assertEventually(() -> assertThat(actionsFor(userId)).isNotEmpty());

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_events SET details = 'edited'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_events"))
                .hasMessageContaining("append-only");
    }

    private List<String> actionsMatching(String targetId, String filters) throws Exception {
        String body = api.get(admin, "/api/v1/admin/audit-events?targetId=" + targetId + filters)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].action");
    }

    private List<String> actionsFor(String targetId) throws Exception {
        String body = api.get(admin, "/api/v1/admin/audit-events?targetId=" + targetId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].action");
    }
}
