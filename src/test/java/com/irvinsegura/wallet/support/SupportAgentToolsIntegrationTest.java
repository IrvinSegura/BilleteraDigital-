package com.irvinsegura.wallet.support;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.application.SupportInactivityService;
import com.irvinsegura.wallet.support.application.SupportInactivityService.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * What helps the team work: pausing, the queue's priority, the customer's earlier cases, notes, how a case ended,
 * finding cases, how each agent does, closing cases whose customer stopped answering, and suggestions for the bot.
 * Customers stop answering at once here (a negative wait puts the limit in the future), so the job acts on the next
 * run.
 */
@SpringBootTest(properties = {
        "wallet.support.inactivity.warn-after=-1s",
        "wallet.support.inactivity.close-after=-1s"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SupportAgentToolsIntegrationTest {

    private static final String CONVERSATION = "/api/v1/support/conversation";
    private static final String AGENT = "/api/v1/support/agent";
    private static final String CASES = AGENT + "/conversations/";
    private static final String ADMIN = "/api/v1/admin/support";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SupportInactivityService inactivity;

    private ApiClient api;
    private TestUser admin;
    private TestUser agent;

    // Assignments look at the whole team and the whole queue, so each test starts without either
    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("DELETE FROM support_bot_suggestions").update();
        jdbc.sql("DELETE FROM support_agent_availability").update();
        jdbc.sql("DELETE FROM support_saved_replies").update();
        jdbc.sql("DELETE FROM support_tickets").update();
        jdbc.sql("DELETE FROM support_bot_questions").update();
        jdbc.sql("DELETE FROM support_messages").update();
        jdbc.sql("DELETE FROM support_attachments").update();
        jdbc.sql("DELETE FROM support_conversations").update();
        jdbc.sql("UPDATE users SET role = 'USER' WHERE role = 'SUPPORT'").update();
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
        agent = newAgent();
    }

    @Test
    void aPausedAgentGetsNoNewCasesUntilTheyComeBack() throws Exception {
        TestUser colleague = newAgent();
        availability(colleague, false).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        availability(agent, false).andExpect(status().isOk());

        String caseId = escalate(api.newUser());
        api.get(agent, AGENT + "/summary")
                .andExpect(jsonPath("$.activeConversationId").doesNotExist())
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.oldestQueuedAt").value(notNullValue()));

        availability(agent, true).andExpect(jsonPath("$.activeConversationId").value(caseId));

        // A paused colleague can't be given the case either
        api.get(agent, AGENT + "/colleagues")
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].busy").value(false));
        api.postJson(agent, CASES + caseId + "/transfer", """
                        {"agentId": "%s"}""".formatted(idOf(colleague)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPORT_AGENT_NOT_AVAILABLE"));
        // Pausing keeps the case being attended
        availability(agent, false).andExpect(jsonPath("$.activeConversationId").value(caseId));
    }

    @Test
    void aProblemWithMoneyGoesFirstInTheQueue() throws Exception {
        availability(agent, false);
        TestUser asking = api.newUser();
        String question = escalate(asking);
        TestUser missingMoney = api.newUser();
        api.postJson(missingMoney, CONVERSATION + "/messages", """
                {"text": "No me llegó mi retiro de ayer"}""").andExpect(status().isOk());
        String urgent = escalate(missingMoney);

        api.get(missingMoney, CONVERSATION).andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.priority").value("HIGH"));
        api.get(asking, CONVERSATION).andExpect(jsonPath("$.queuePosition").value(2))
                .andExpect(jsonPath("$.priority").value("NORMAL"));
        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.queuedHighPriority").value(1));

        availability(agent, true).andExpect(jsonPath("$.activeConversationId").value(urgent));
        api.get(asking, CONVERSATION).andExpect(jsonPath("$.queuePosition").value(1));
        api.get(admin, ADMIN + "/conversations?status=QUEUED")
                .andExpect(jsonPath("$.items[0].id").value(question))
                .andExpect(jsonPath("$.items[0].escalatedAt").value(notNullValue()));
    }

    @Test
    void notesStayWithTheTeam() throws Exception {
        TestUser customer = api.newUser();
        String caseId = attended(customer);

        api.postJson(agent, CASES + caseId + "/notes", """
                        {"text": "Ya revisé su retiro: está en camino"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text").value("Ya revisé su retiro: está en camino"))
                .andExpect(jsonPath("$[0].authorName").value(notNullValue()));
        api.postJson(agent, CASES + caseId + "/notes", """
                {"text": " "}""").andExpect(status().isBadRequest());

        api.get(agent, CASES + caseId + "/notes").andExpect(jsonPath("$", hasSize(1)));
        assertThat(api.get(customer, CONVERSATION).andReturn().getResponse().getContentAsString())
                .doesNotContain("Ya revisé");
        api.get(newAgent(), CASES + caseId + "/notes").andExpect(status().isNotFound());
        api.get(customer, CASES + caseId + "/notes").andExpect(status().isForbidden());
        api.get(admin, ADMIN + "/conversations/" + caseId)
                .andExpect(jsonPath("$.notes[0].text").value("Ya revisé su retiro: está en camino"));
    }

    @Test
    void theAgentSaysHowACaseEndedAndSeesTheCustomersEarlierOnes() throws Exception {
        TestUser customer = api.newUser();
        String first = attended(customer);
        resolve(first, "CUSTOMER_CLOSED").andExpect(status().isBadRequest());
        resolve(first, "INFORMATION").andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("INFORMATION"));

        String second = attended(customer);
        api.get(agent, CASES + second + "/history")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].resolution").value("INFORMATION"));

        // The customer closing it is recorded apart, and a case only the bot saw has no ending to report
        api.postJson(customer, CONVERSATION + "/close", "").andExpect(status().isOk());
        TestUser withTheBot = api.newUser();
        api.postJson(withTheBot, CONVERSATION + "/messages", """
                {"text": "hola"}""").andExpect(status().isOk());
        api.postJson(withTheBot, CONVERSATION + "/close", "").andExpect(status().isNoContent());
        api.get(admin, ADMIN + "/summary")
                .andExpect(jsonPath("$.resolutions.INFORMATION").value(1))
                .andExpect(jsonPath("$.resolutions.CUSTOMER_CLOSED").value(1));
        api.get(admin, ADMIN + "/conversations/" + second)
                .andExpect(jsonPath("$.conversation.resolution").value("CUSTOMER_CLOSED"));
    }

    @Test
    void casesAreFoundByTheCustomersNameOrTheirFolio() throws Exception {
        TestUser marisol = api.newUser();
        api.rename(marisol, "Marisol Quintanar");
        String closed = attended(marisol);
        resolve(closed, "SOLVED").andExpect(status().isOk());
        TestUser other = api.newUser();
        api.rename(other, "Teodoro Báez");
        String open = attended(other);

        cases(agent, "?q=quintanar").andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(closed));
        cases(agent, "?q=" + open.substring(0, 8)).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(open));
        cases(agent, "?status=RESOLVED").andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(closed));
        cases(agent, "?status=ASSIGNED&q=quintanar").andExpect(jsonPath("$.items", hasSize(0)));
        cases(agent, "?q=nadie-se-llama-asi").andExpect(jsonPath("$.items", hasSize(0)));
        // Agents never see the customer's email, so it finds nothing for them; administrators find it
        cases(agent, "?q=" + marisol.email()).andExpect(jsonPath("$.items", hasSize(0)));
        api.get(admin, ADMIN + "/conversations?q=" + marisol.email())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(closed));
        // Another agent's cases are never theirs to find
        cases(newAgent(), "?q=quintanar").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void anAgentAndTheAdministratorsSeeHowTheyDo() throws Exception {
        TestUser customer = api.newUser();
        String caseId = attended(customer);
        api.postJson(agent, CASES + caseId + "/messages", """
                {"text": "Hola, te ayudo"}""").andExpect(status().isOk());
        resolve(caseId, "SOLVED").andExpect(status().isOk());
        api.postJson(customer, CONVERSATION + "/survey", """
                {"score": 5}""").andExpect(status().isNoContent());

        api.get(agent, AGENT + "/metrics?days=7")
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.attended").value(1))
                .andExpect(jsonPath("$.resolved").value(1))
                .andExpect(jsonPath("$.firstResponseSeconds").value(notNullValue()))
                .andExpect(jsonPath("$.handleSeconds").value(notNullValue()))
                .andExpect(jsonPath("$.averageScore").value(5.0));
        newAgent();
        api.get(admin, ADMIN + "/agents")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '%s')].metrics.resolved".formatted(idOf(agent))).value(hasItem(1)))
                .andExpect(jsonPath("$[?(@.id == '%s')].available".formatted(idOf(agent))).value(hasItem(true)));
        api.get(agent, ADMIN + "/agents").andExpect(status().isForbidden());
    }

    @Test
    void aCustomerWhoStopsAnsweringIsWarnedAndThenTheCaseCloses() throws Exception {
        TestUser customer = api.newUser();
        String caseId = attended(customer);
        // Nothing to wait on until the agent writes
        assertThat(inactivity.handleInactiveCases()).isEqualTo(new Outcome(0, 0));
        reply(caseId);

        assertThat(inactivity.handleInactiveCases()).isEqualTo(new Outcome(1, 0));
        api.get(customer, CONVERSATION)
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.messages[*].event", hasItem("INACTIVITY_WARNING")));

        String next = escalate(api.newUser());
        assertThat(inactivity.handleInactiveCases()).isEqualTo(new Outcome(0, 1));
        api.get(customer, CONVERSATION)
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("NO_REPLY"))
                .andExpect(jsonPath("$.messages[*].event", hasItem("INACTIVITY_CLOSED")))
                .andExpect(jsonPath("$.awaitsSurvey").value(true));
        // The agent is free for the next one
        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").value(next));
    }

    @Test
    void aCustomerWhoAnswersOrATicketOpenKeepsTheCaseOpen() throws Exception {
        TestUser customer = api.newUser();
        String caseId = attended(customer);
        reply(caseId);
        inactivity.handleInactiveCases();

        api.postJson(customer, CONVERSATION + "/messages", """
                {"text": "Sigo aquí"}""").andExpect(status().isOk());
        assertThat(inactivity.handleInactiveCases()).isEqualTo(new Outcome(0, 0));
        api.get(customer, CONVERSATION).andExpect(jsonPath("$.status").value("ASSIGNED"));
        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.lastCustomerMessageAt").value(notNullValue()));

        // Waiting on the administrators is not waiting on the customer
        api.postJson(agent, CASES + caseId + "/tickets", """
                {"category": "OTHER", "description": "Revisar su cuenta"}""").andExpect(status().isCreated());
        reply(caseId);
        assertThat(inactivity.handleInactiveCases()).isEqualTo(new Outcome(0, 0));
        api.get(customer, CONVERSATION).andExpect(jsonPath("$.messages[*].event", not(hasItem("INACTIVITY_CLOSED"))));
    }

    @Test
    void anAgentSuggestsWhatTheBotShouldLearn() throws Exception {
        TestUser customer = api.newUser();
        String caseId = attended(customer);

        String id = read(api.postJson(agent, CASES + caseId + "/bot-suggestions", """
                        {"question": "¿Puedo tener dos cuentas?", "answer": "No, una por persona."}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                // The customer's language, which says what knowledge files it belongs in
                .andExpect(jsonPath("$.language").value("en")), "$.id");
        api.postJson(agent, CASES + caseId + "/bot-suggestions", """
                {"question": "", "answer": "x"}""").andExpect(status().isBadRequest());
        api.postJson(newAgent(), CASES + caseId + "/bot-suggestions", """
                {"question": "a", "answer": "b"}""").andExpect(status().isNotFound());

        api.get(admin, ADMIN + "/bot/suggestions?status=OPEN")
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.items[0].question").value("¿Puedo tener dos cuentas?"))
                .andExpect(jsonPath("$.items[0].agentName").value(notNullValue()));
        review(id, "OPEN").andExpect(status().isBadRequest());
        review(id, "ADDED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADDED"))
                .andExpect(jsonPath("$.reviewedByName").value(notNullValue()));
        review(id, "DISMISSED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPORT_BOT_SUGGESTION_REVIEWED"));
        review(UUID.randomUUID().toString(), "ADDED").andExpect(status().isNotFound());
        api.get(admin, ADMIN + "/bot/suggestions?status=OPEN").andExpect(jsonPath("$.open").value(0));
    }

    private ResultActions availability(TestUser someone, boolean available) throws Exception {
        return mockMvc.perform(put(AGENT + "/availability")
                .header("Authorization", someone.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"available": %s}""".formatted(available)));
    }

    private ResultActions cases(TestUser someone, String query) throws Exception {
        return api.get(someone, AGENT + "/conversations" + query).andExpect(status().isOk());
    }

    private ResultActions resolve(String caseId, String reason) throws Exception {
        return api.postJson(agent, CASES + caseId + "/resolve", """
                {"reason": "%s"}""".formatted(reason));
    }

    private ResultActions review(String suggestionId, String outcome) throws Exception {
        return api.postJson(admin, ADMIN + "/bot/suggestions/" + suggestionId + "/review", """
                {"status": "%s"}""".formatted(outcome));
    }

    private void reply(String caseId) throws Exception {
        api.postJson(agent, CASES + caseId + "/messages", """
                {"text": "¿Me confirmas tu folio?"}""").andExpect(status().isOk());
    }

    // A case of the customer's that has reached the agent
    private String attended(TestUser customer) throws Exception {
        String caseId = escalate(customer);
        assertEventually(() -> api.get(agent, AGENT + "/summary")
                .andExpect(jsonPath("$.activeConversationId").value(caseId)));
        return caseId;
    }

    private String escalate(TestUser someone) throws Exception {
        return read(api.postJson(someone, CONVERSATION + "/escalate", ""), "$.id");
    }

    private TestUser newAgent() throws Exception {
        TestUser account = api.newUser();
        api.postJson(admin, "/api/v1/admin/users/" + idOf(account) + "/role", """
                {"role": "SUPPORT", "reason": "Joined the team"}""").andExpect(status().isOk());
        return api.login(account.email(), PASSWORD);
    }

    private String idOf(TestUser account) throws Exception {
        return read(api.get(account, "/api/v1/users/me"), "$.id");
    }
}
