package com.irvinsegura.wallet.notifications;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.Mailbox;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NotificationIntegrationTest {

    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final Predicate<String> NOT_SIGN_IN = subject -> !"New sign-in to your account".equals(subject);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private Mailbox mailbox;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, mailpit);
        mailbox = new Mailbox(mailpit);
    }

    @Test
    void transferNotifiesSenderAndRecipientInTheAppAndByEmail() throws Exception {
        TestUser sender = api.newUser();
        TestUser recipient = api.newUser();
        String sourceWalletId = api.openWalletId(sender, "MXN");
        api.openWalletId(recipient, "MXN");
        api.deposit(sender, sourceWalletId, "100.00").andExpect(status().isCreated());
        api.transfer(sender, sourceWalletId, recipient.email(), "30.25", newKey()).andExpect(status().isCreated());

        // Listeners run after commit, asynchronously
        assertEventually(() -> assertThat(typesFor(sender))
                .containsExactly("TRANSFER_SENT", "DEPOSIT_RECEIVED", "WELCOME"));
        assertEventually(() -> assertThat(typesFor(recipient)).containsExactly("TRANSFER_RECEIVED", "WELCOME"));

        String received = inbox(recipient, "");
        assertThat((String) JsonPath.read(received, "$.items[0].body")).isEqualTo("Ana López sent you 30.25 MXN.");
        assertThat((Boolean) JsonPath.read(received, "$.items[0].read")).isFalse();
        assertThat((String) JsonPath.read(inbox(sender, ""), "$.items[0].body"))
                .isEqualTo("You sent 30.25 MXN to %s.".formatted(recipient.email()));

        // The welcome and the verification email are sent at the same time, so their order varies. The sign-in
        // emails are left out: how many there are depends on how often the test signs in
        assertEventually(() -> assertThat(mailbox.subjectsFor(recipient.email())).filteredOn(NOT_SIGN_IN)
                .hasSize(3)
                .startsWith("Money received")
                .contains("Welcome to Digital Wallet", "Confirm your email address"));
        assertThat(mailbox.snippetsFor(recipient.email()).getFirst()).contains("Ana López sent you money");
        assertEventually(() -> assertThat(mailbox.subjectsFor(sender.email())).filteredOn(NOT_SIGN_IN)
                .containsExactlyInAnyOrder("Transfer sent", "Deposit received", "Welcome to Digital Wallet",
                        "Confirm your email address"));
    }

    @Test
    void usersMarkTheirNotificationsAsRead() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "USD");
        api.deposit(user, walletId, "10.00").andExpect(status().isCreated());
        assertEventually(() -> assertThat(typesFor(user)).containsExactly("DEPOSIT_RECEIVED", "WELCOME"));
        api.get(user, NOTIFICATIONS + "/unread-count").andExpect(jsonPath("$.count").value(2));

        String depositNotificationId = read(api.get(user, NOTIFICATIONS), "$.items[0].id");
        markRead(user, depositNotificationId).andExpect(status().isNoContent());
        // Marking again is not an error
        markRead(user, depositNotificationId).andExpect(status().isNoContent());

        api.get(user, NOTIFICATIONS + "/unread-count").andExpect(jsonPath("$.count").value(1));
        assertThat((List<String>) JsonPath.read(inbox(user, "?unreadOnly=true"), "$.items[*].type"))
                .containsExactly("WELCOME");
        String all = inbox(user, "");
        assertThat((Boolean) JsonPath.read(all, "$.items[0].read")).isTrue();
        assertThat((String) JsonPath.read(all, "$.items[0].readAt")).isNotNull();

        mockMvc.perform(post(NOTIFICATIONS + "/read-all").header("Authorization", user.bearer()))
                .andExpect(status().isNoContent());
        api.get(user, NOTIFICATIONS + "/unread-count").andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void usersDeleteOneNotificationOrEveryReadOne() throws Exception {
        TestUser user = api.newUser();
        TestUser stranger = api.newUser();
        String walletId = api.openWalletId(user, "USD");
        api.deposit(user, walletId, "10.00").andExpect(status().isCreated());
        api.deposit(user, walletId, "5.00").andExpect(status().isCreated());
        assertEventually(() -> assertThat(typesFor(user))
                .containsExactly("DEPOSIT_RECEIVED", "DEPOSIT_RECEIVED", "WELCOME"));
        List<String> ids = JsonPath.read(inbox(user, ""), "$.items[*].id");

        deleteNotification(stranger, ids.get(0)).andExpect(status().isNotFound());
        deleteNotification(user, ids.get(0)).andExpect(status().isNoContent());
        deleteNotification(user, ids.get(0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        assertThat((List<String>) JsonPath.read(inbox(user, ""), "$.items[*].id")).containsExactly(ids.get(1), ids.get(2));

        // Only what was read goes
        markRead(user, ids.get(1)).andExpect(status().isNoContent());
        mockMvc.perform(delete(NOTIFICATIONS + "/read").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));
        assertThat((List<String>) JsonPath.read(inbox(user, ""), "$.items[*].type")).containsExactly("WELCOME");
    }

    @Test
    void otherUsersNotificationsAreNotFound() throws Exception {
        TestUser owner = api.newUser();
        TestUser stranger = api.newUser();
        assertEventually(() -> assertThat(typesFor(owner)).containsExactly("WELCOME"));
        String notificationId = read(api.get(owner, NOTIFICATIONS), "$.items[0].id");

        markRead(stranger, notificationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        markRead(stranger, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        api.get(owner, NOTIFICATIONS + "/unread-count").andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void freezingAWalletTellsItsOwnerWithoutTheInternalReason() throws Exception {
        TestUser admin = api.admin();
        TestUser owner = api.newUser();
        String walletId = api.openWalletId(owner, "MXN");

        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/freeze", """
                {"reason": "Chargeback investigation"}""").andExpect(status().isOk());
        assertEventually(() -> assertThat(typesFor(owner)).containsExactly("WALLET_FROZEN", "WELCOME"));
        api.postJson(admin, "/api/v1/admin/wallets/" + walletId + "/unfreeze", """
                {"reason": "Resolved"}""").andExpect(status().isOk());
        assertEventually(() -> assertThat(typesFor(owner))
                .containsExactly("WALLET_UNFROZEN", "WALLET_FROZEN", "WELCOME"));

        String body = inbox(owner, "");
        assertThat((List<String>) JsonPath.read(body, "$.items[*].body")).noneMatch(text -> text.contains("Chargeback"));
        assertThat((String) JsonPath.read(body, "$.items[1].referenceId")).isEqualTo(walletId);
    }

    @Test
    void lockoutWarnsTheAccountOwner() throws Exception {
        TestUser user = api.newUser();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    {"email": "%s", "password": "wrong password 123"}""".formatted(user.email())));
        }

        // The access token issued before the lockout still works
        assertEventually(() -> assertThat(typesFor(user)).containsExactly("ACCOUNT_LOCKED", "WELCOME"));
        assertEventually(() -> assertThat(mailbox.subjectsFor(user.email()))
                .contains("Sign-in blocked after failed attempts"));
    }

    @Test
    void aRedeliveredEventCreatesOneNotificationAndOneEmail() throws Exception {
        TestUser user = api.newUser();
        UUID userId = UUID.fromString(read(api.get(user, "/api/v1/users/me"), "$.id"));
        UUID depositId = UUID.randomUUID();
        DepositCompleted event = new DepositCompleted(depositId, userId, UUID.randomUUID(), 1234, "MXN",
                Instant.now());

        // Same event again once the first delivery is done, as after a crash between the listener's commit and the
        // publication's completion. Publishing both at once would race inside Spring Modulith, which tracks equal
        // events to the same listener as one publication in progress and can leave the other one incomplete.
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        assertEventually(() -> assertThat(mailbox.subjectsFor(user.email())).contains("Deposit received"));
        assertEventually(() -> assertThat(pendingPublicationsOf(depositId)).isZero());
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        assertEventually(() -> assertThat(pendingPublicationsOf(depositId)).isZero());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications WHERE reference_id = ?",
                Long.class, depositId)).isEqualTo(1);
        assertThat(mailbox.subjectsFor(user.email())).filteredOn("Deposit received"::equals).hasSize(1);
    }

    private long pendingPublicationsOf(UUID referenceId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
                Long.class, "%" + referenceId + "%");
    }

    private ResultActions deleteNotification(TestUser user, String notificationId) throws Exception {
        return mockMvc.perform(delete(NOTIFICATIONS + "/" + notificationId).header("Authorization", user.bearer()));
    }

    private ResultActions markRead(TestUser user, String notificationId) throws Exception {
        return mockMvc.perform(post(NOTIFICATIONS + "/" + notificationId + "/read")
                .header("Authorization", user.bearer()));
    }

    @Test
    void moneyEmailsCanBeTurnedOffWhileTheInboxKeepsEverything() throws Exception {
        TestUser sender = api.newUser();
        TestUser recipient = api.newUser();
        String sourceWalletId = api.openWalletId(sender, "MXN");
        api.openWalletId(recipient, "MXN");
        api.get(recipient, NOTIFICATIONS + "/preferences")
                .andExpect(jsonPath("$.emailMoneyIn").value(true))
                .andExpect(jsonPath("$.emailMoneyOut").value(true));

        putPreferences(recipient, """
                {"emailMoneyIn": false, "emailMoneyOut": true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailMoneyIn").value(false));
        putPreferences(recipient, """
                {"emailMoneyIn": false}""").andExpect(status().isBadRequest());
        api.deposit(sender, sourceWalletId, "100.00").andExpect(status().isCreated());
        api.transfer(sender, sourceWalletId, recipient.email(), "30.25", newKey()).andExpect(status().isCreated());

        assertEventually(() -> assertThat(typesFor(recipient)).containsExactly("TRANSFER_RECEIVED", "WELCOME"));
        // The sender's copy goes out with the same publication run, so once it arrives the recipient's would have
        assertEventually(() -> assertThat(mailbox.subjectsFor(sender.email())).contains("Transfer sent"));
        String recipientId = read(api.get(recipient, "/api/v1/users/me"), "$.id");
        assertEventually(() -> assertThat(pendingPublicationsFor(recipientId)).isZero());
        assertThat(mailbox.subjectsFor(recipient.email())).doesNotContain("Money received");
    }

    @Test
    void theStreamPushesEachNewNotificationToTheOwner() throws Exception {
        TestUser user = api.newUser();
        TestUser other = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        MvcResult stream = openStream(user);
        MvcResult otherStream = openStream(other);

        api.deposit(user, walletId, "12.00").andExpect(status().isCreated());

        assertEventually(() -> assertThat(stream.getResponse().getContentAsString())
                .contains("event:ready")
                .contains("event:notification")
                .contains("\"type\":\"DEPOSIT_RECEIVED\""));
        assertThat(otherStream.getResponse().getContentAsString()).doesNotContain("DEPOSIT_RECEIVED");
        assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private MvcResult openStream(TestUser user) throws Exception {
        return mockMvc.perform(get(NOTIFICATIONS + "/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private ResultActions putPreferences(TestUser user, String json) throws Exception {
        return mockMvc.perform(put(NOTIFICATIONS + "/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // Events naming the user that a listener hasn't finished with yet
    private long pendingPublicationsFor(String userId) {
        Long pending = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE ?",
                Long.class, "%" + userId + "%");
        return pending == null ? 0 : pending;
    }

    private String inbox(TestUser user, String query) throws Exception {
        return api.get(user, NOTIFICATIONS + query)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> typesFor(TestUser user) throws Exception {
        return JsonPath.read(inbox(user, ""), "$.items[*].type");
    }
}
