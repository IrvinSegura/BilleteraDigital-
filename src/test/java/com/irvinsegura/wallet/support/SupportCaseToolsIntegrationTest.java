package com.irvinsegura.wallet.support;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * What the agent attending a case has besides the chat: where the customer's account stands, tickets about the
 * account and not only about an operation, giving the case back or to a colleague, and their saved replies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SupportCaseToolsIntegrationTest {

    private static final String CONVERSATION = "/api/v1/support/conversation";
    private static final String AGENT = "/api/v1/support/agent";
    private static final String CASES = AGENT + "/conversations/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcClient jdbc;

    private ApiClient api;
    private TestUser admin;
    private TestUser customer;
    private TestUser agent;
    private String caseId;

    // Assignments look at the whole team and the whole queue, so each test starts without either
    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("DELETE FROM support_saved_replies").update();
        jdbc.sql("DELETE FROM support_tickets").update();
        jdbc.sql("DELETE FROM support_bot_questions").update();
        jdbc.sql("DELETE FROM support_messages").update();
        jdbc.sql("DELETE FROM support_attachments").update();
        jdbc.sql("DELETE FROM support_conversations").update();
        jdbc.sql("UPDATE users SET role = 'USER' WHERE role = 'SUPPORT'").update();
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
        customer = api.newUser();
        api.openWalletId(customer, "MXN");
        agent = newAgent();
        caseId = escalate(customer);
        assertEventually(() -> api.get(agent, AGENT + "/summary")
                .andExpect(jsonPath("$.activeConversationId").value(caseId)));
    }

    @Test
    void theAgentSeesWhereTheCustomersAccountStands() throws Exception {
        api.get(agent, CASES + caseId + "/customer")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist())
                .andExpect(jsonPath("$.identityStatus").value("NONE"))
                .andExpect(jsonPath("$.wallets[0].currency").value("MXN"))
                .andExpect(jsonPath("$.wallets[0].frozen").value(false))
                // Never contact data
                .andExpect(jsonPath("$.email").doesNotExist());

        api.get(newAgent(), CASES + caseId + "/customer").andExpect(status().isNotFound());
        api.postJson(agent, CASES + caseId + "/resolve", "").andExpect(status().isOk());
        api.get(agent, CASES + caseId + "/customer").andExpect(status().isUnprocessableContent());
    }

    @Test
    void aTicketCanBeAboutTheAccountAndNotAnOperation() throws Exception {
        String ticketId = read(ticket("""
                        {"category": "IDENTITY", "description": "Su INE lleva cuatro días en revisión"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("IDENTITY"))
                .andExpect(jsonPath("$.operationId").doesNotExist()), "$.id");

        // One open per customer and subject
        ticket("""
                {"category": "IDENTITY", "description": "Otra vez"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPORT_TICKET_ALREADY_OPEN"));
        ticket("""
                {"category": "ACCOUNT_ACCESS", "description": "No puede entrar"}""").andExpect(status().isCreated());
        // An operation ticket needs its operation, and the rest can't have one
        ticket("""
                {"category": "OPERATION", "description": "Sin operación"}""").andExpect(status().isBadRequest());
        ticket("""
                {"category": "OTHER", "operationId": "%s", "description": "Con operación"}"""
                .formatted(java.util.UUID.randomUUID())).andExpect(status().isBadRequest());

        api.get(admin, "/api/v1/admin/support/tickets/" + ticketId)
                .andExpect(jsonPath("$.category").value("IDENTITY"))
                .andExpect(jsonPath("$.operation").doesNotExist());
        api.postJson(admin, "/api/v1/admin/support/tickets/" + ticketId + "/resolve", """
                        {"resolution": "Revisamos y aprobamos tu INE."}""")
                .andExpect(status().isOk());
        assertEventually(() -> assertThat(api.get(admin, "/api/v1/admin/audit-events?targetId=" + idOf(customer))
                .andReturn().getResponse().getContentAsString()).contains("ticket " + ticketId + ": identity"));
    }

    @Test
    void aReleasedCaseGoesToAColleagueAndNotBackToWhoGaveItUp() throws Exception {
        TestUser colleague = newAgent();

        api.postJson(agent, CASES + caseId + "/release", "").andExpect(status().isNoContent());

        api.get(colleague, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").value(caseId));
        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").doesNotExist());
        api.get(agent, CASES + caseId).andExpect(status().isNotFound());
        api.get(customer, CONVERSATION).andExpect(jsonPath("$.messages[*].event", hasItem("RELEASED")));
    }

    @Test
    void aCaseGoesOnlyToAFreeColleague() throws Exception {
        TestUser busy = newAgent();
        String otherCase = escalate(api.newUser());
        assertEventually(() -> api.get(busy, AGENT + "/summary")
                .andExpect(jsonPath("$.activeConversationId").value(otherCase)));
        TestUser free = newAgent();

        api.get(agent, AGENT + "/colleagues")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == '%s')].busy".formatted(idOf(busy))).value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == '%s')].busy".formatted(idOf(free))).value(hasItem(false)));

        transfer(busy).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPORT_AGENT_NOT_AVAILABLE"));
        transfer(customer).andExpect(status().isConflict());
        transfer(free).andExpect(status().isNoContent());

        api.get(free, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").value(caseId));
        api.get(free, CASES + caseId).andExpect(status().isOk());
        api.get(agent, CASES + caseId).andExpect(status().isNotFound());
        api.get(customer, CONVERSATION).andExpect(jsonPath("$.messages[*].event", hasItem("TRANSFERRED")));
    }

    @Test
    void eachAgentKeepsTheirOwnSavedReplies() throws Exception {
        String id = read(api.postJson(agent, AGENT + "/replies", """
                        {"title": "INE en revisión", "text": "La revisión de tu INE toma hasta 48 horas."}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("INE en revisión")), "$.id");
        api.postJson(agent, AGENT + "/replies", """
                {"title": "Antes", "text": "Hola"}""").andExpect(status().isCreated());

        api.get(agent, AGENT + "/replies")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Antes"));
        TestUser colleague = newAgent();
        api.get(colleague, AGENT + "/replies").andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete(AGENT + "/replies/" + id).header("Authorization", colleague.bearer()))
                .andExpect(status().isNotFound());
        api.get(customer, AGENT + "/replies").andExpect(status().isForbidden());
        api.postJson(agent, AGENT + "/replies", """
                {"title": "", "text": "x"}""").andExpect(status().isBadRequest());

        mockMvc.perform(delete(AGENT + "/replies/" + id).header("Authorization", agent.bearer()))
                .andExpect(status().isNoContent());
        api.get(agent, AGENT + "/replies").andExpect(jsonPath("$", hasSize(1)));
    }

    private ResultActions ticket(String json) throws Exception {
        return api.postJson(agent, CASES + caseId + "/tickets", json);
    }

    private ResultActions transfer(TestUser colleague) throws Exception {
        return api.postJson(agent, CASES + caseId + "/transfer", """
                {"agentId": "%s"}""".formatted(idOf(colleague)));
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
