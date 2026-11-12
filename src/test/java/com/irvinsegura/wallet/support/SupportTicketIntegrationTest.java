package com.irvinsegura.wallet.support;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Problems with money in two levels: the agent sees the customer's operations while attending them and escalates;
 * an administrator settles the ticket and the customer is told.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SupportTicketIntegrationTest {

    private static final String CONVERSATION = "/api/v1/support/conversation";
    private static final String AGENT = "/api/v1/support/agent/conversations/";
    private static final String TICKETS = "/api/v1/admin/support/tickets";
    private static final String MY_TICKETS = "/api/v1/support/agent/tickets";

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
    private String depositId;

    // Assignments look at the whole team and the whole queue, so each test starts without either
    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("DELETE FROM support_tickets").update();
        jdbc.sql("DELETE FROM support_bot_questions").update();
        jdbc.sql("DELETE FROM support_messages").update();
        jdbc.sql("DELETE FROM support_attachments").update();
        jdbc.sql("DELETE FROM support_conversations").update();
        jdbc.sql("UPDATE users SET role = 'USER' WHERE role = 'SUPPORT'").update();
        api = new ApiClient(mockMvc, mailpit);
        admin = api.admin();
        customer = api.newUser();
        String walletId = api.openWalletId(customer, "MXN");
        depositId = read(api.deposit(customer, walletId, "250.00").andExpect(status().isCreated()), "$.id");
        agent = newAgent();
        caseId = read(api.postJson(customer, CONVERSATION + "/escalate", ""), "$.id");
        assertEventually(() -> api.get(agent, "/api/v1/support/agent/summary")
                .andExpect(jsonPath("$.activeConversationId").value(caseId)));
    }

    @Test
    void theAgentSeesTheCustomersOperationsOnlyWhileAttendingThem() throws Exception {
        api.get(agent, AGENT + caseId + "/operations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("deposit"))
                .andExpect(jsonPath("$[0].id").value(depositId))
                .andExpect(jsonPath("$[0].amount.amount").value("250.00"))
                // Read only, and never a balance
                .andExpect(jsonPath("$[0].balanceAfter").doesNotExist());

        TestUser otherAgent = newAgent();
        api.get(otherAgent, AGENT + caseId + "/operations").andExpect(status().isNotFound());
        api.get(customer, AGENT + caseId + "/operations").andExpect(status().isForbidden());

        api.postJson(agent, AGENT + caseId + "/resolve", "").andExpect(status().isOk());
        api.get(agent, AGENT + caseId + "/operations").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUPPORT_CONVERSATION_CLOSED"));
    }

    @Test
    void theAgentEscalatesAndAnAdministratorSettlesIt() throws Exception {
        String ticketId = read(api.postJson(agent, AGENT + caseId + "/tickets", """
                        {"operationId": "%s", "description": "El cliente pagó y el depósito no llegó"}""".formatted(depositId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.operationKind").value("deposit"))
                .andExpect(jsonPath("$.customerEmail").doesNotExist()), "$.id");

        // One open ticket per operation
        api.postJson(agent, AGENT + caseId + "/tickets", """
                        {"operationId": "%s", "description": "Otra vez"}""".formatted(depositId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPORT_TICKET_ALREADY_OPEN"));
        // The customer reads in the chat that it went to the administrators
        api.get(customer, CONVERSATION).andExpect(jsonPath("$.messages[*].event", hasItem("TICKET_OPENED")));

        api.get(admin, TICKETS + "?status=OPEN")
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ticketId));
        api.get(admin, TICKETS + "/" + ticketId)
                .andExpect(jsonPath("$.customerEmail").value(customer.email()))
                .andExpect(jsonPath("$.operation.id").value(depositId))
                .andExpect(jsonPath("$.description").value("El cliente pagó y el depósito no llegó"));

        api.postJson(admin, TICKETS + "/" + ticketId + "/resolve", """
                        {"resolution": "Confirmamos el pago con el proveedor y acreditamos el depósito."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedByName").exists());
        api.postJson(admin, TICKETS + "/" + ticketId + "/resolve", """
                        {"resolution": "Otra vez"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUPPORT_TICKET_CLOSED"));

        assertEventually(() -> assertThat(notifications(customer, "SUPPORT_TICKET_RESOLVED")).contains(ticketId));
        api.get(agent, AGENT + caseId + "/tickets").andExpect(jsonPath("$[0].status").value("RESOLVED"));
        assertEventually(() -> assertThat(api.get(admin, "/api/v1/admin/audit-events?targetId=" + idOf(customer))
                .andReturn().getResponse().getContentAsString())
                .contains("SUPPORT_TICKET_OPENED", "SUPPORT_TICKET_RESOLVED"));
    }

    @Test
    void theAgentFollowsWhatTheyEscalatedAndHearsHowItWasSettled() throws Exception {
        String ticketId = read(api.postJson(agent, AGENT + caseId + "/tickets", """
                        {"operationId": "%s", "description": "El depósito no llegó"}""".formatted(depositId))
                .andExpect(status().isCreated()), "$.id");
        // Even after closing the case, it stays in the agent's own list
        api.postJson(agent, AGENT + caseId + "/resolve", "").andExpect(status().isOk());

        api.get(agent, MY_TICKETS + "?status=OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ticketId))
                .andExpect(jsonPath("$.items[0].conversationId").value(caseId))
                .andExpect(jsonPath("$.items[0].customerName").exists())
                // Agents never read the customer's email
                .andExpect(jsonPath("$.items[0].customerEmail").doesNotExist());
        TestUser otherAgent = newAgent();
        api.get(otherAgent, MY_TICKETS).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.open").value(0));
        api.get(customer, MY_TICKETS).andExpect(status().isForbidden());

        api.postJson(admin, TICKETS + "/" + ticketId + "/resolve", """
                        {"resolution": "Acreditamos el depósito."}""").andExpect(status().isOk());

        assertEventually(() -> assertThat(notifications(agent, "SUPPORT_ESCALATION_RESOLVED")).contains(ticketId));
        api.get(agent, MY_TICKETS + "?status=OPEN").andExpect(jsonPath("$.items").isEmpty());
        api.get(agent, MY_TICKETS + "?status=RESOLVED")
                .andExpect(jsonPath("$.open").value(0))
                .andExpect(jsonPath("$.items[0].resolution").value("Acreditamos el depósito."));
    }

    @Test
    void onlyTheCustomersOwnOperationsCanBeEscalated() throws Exception {
        TestUser someoneElse = api.newUser();
        String theirDeposit = read(api.deposit(someoneElse, api.openWalletId(someoneElse, "MXN"), "10.00"), "$.id");

        api.postJson(agent, AGENT + caseId + "/tickets", """
                        {"operationId": "%s", "description": "No es de este cliente"}""".formatted(theirDeposit))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPORT_OPERATION_NOT_FOUND"));
        api.get(customer, CONVERSATION).andExpect(jsonPath("$.messages[*].event", not(hasItem("TICKET_OPENED"))));
    }

    @Test
    void onlyAdministratorsSeeTheTickets() throws Exception {
        api.get(agent, TICKETS).andExpect(status().isForbidden());
        api.get(customer, TICKETS).andExpect(status().isForbidden());
        // Nor can a customer open one about themselves
        api.postJson(customer, AGENT + caseId + "/tickets", """
                {"operationId": "%s", "description": "Yo mismo"}""".formatted(depositId))
                .andExpect(status().isForbidden());
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

    private List<String> notifications(TestUser account, String type) throws Exception {
        String body = api.get(account, "/api/v1/notifications").andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[?(@.type == '%s')].referenceId".formatted(type));
    }
}
