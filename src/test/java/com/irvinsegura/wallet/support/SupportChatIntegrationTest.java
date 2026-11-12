package com.irvinsegura.wallet.support;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SupportChatIntegrationTest {

    private static final String CONVERSATION = "/api/v1/support/conversation";
    private static final String AGENT = "/api/v1/support/agent";
    private static final String UNKNOWN_QUESTION = """
            {"text": "tengo un problema con mi perro y mi hermana"}""";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcClient jdbc;

    private ApiClient api;
    private TestUser admin;
    private TestUser user;

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
        user = api.newUser();
        mockMvc.perform(put("/api/v1/users/me/language").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"language": "es"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void noConversationUntilTheUserWrites() throws Exception {
        api.get(user, CONVERSATION).andExpect(status().isNoContent());
    }

    @Test
    void theBotAnswersWhatItKnowsAndAsksIfItHelped() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "¿Cómo deposito dinero?"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOT"))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].sender").value("USER"))
                .andExpect(jsonPath("$.messages[1].sender").value("BOT"))
                .andExpect(jsonPath("$.messages[1].asksFeedback").value(true))
                .andExpect(jsonPath("$.messages[1].text", containsString("Depositar")));
    }

    @Test
    void greetingsDoNotAskIfTheyHelped() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "hola"}""")
                .andExpect(jsonPath("$.status").value("BOT"))
                .andExpect(jsonPath("$.messages[1].asksFeedback").value(false));
    }

    @Test
    void theBotAnswersInTheLanguageOfTheUser() throws Exception {
        api.postJson(api.newUser(), CONVERSATION + "/messages", """
                {"text": "How do I deposit money?"}""")
                .andExpect(jsonPath("$.status").value("BOT"))
                .andExpect(jsonPath("$.messages[1].text", containsString("choose **Deposit**")));
    }

    @Test
    void whatTheBotCannotReadIsAskedAgainAndStaysWithIt() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "k"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOT"))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].sender").value("BOT"))
                .andExpect(jsonPath("$.messages[1].asksFeedback").value(false))
                .andExpect(jsonPath("$.messages[1].text", containsString("No te entendí")));
    }

    @Test
    void whatTheBotPartlyUnderstandsOffersQuestionsThatCanBePicked() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "como retiro a mi bnaoc"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOT"))
                .andExpect(jsonPath("$.messages[1].suggestions[0]").value("¿Cómo retiro dinero a mi banco?"));

        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "¿Cómo retiro dinero a mi banco?"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[3].suggestions", hasSize(0)))
                .andExpect(jsonPath("$.messages[3].asksFeedback").value(true))
                .andExpect(jsonPath("$.messages[3].text", containsString("Retirar")));

        api.get(user, CONVERSATION)
                .andExpect(jsonPath("$.messages[1].suggestions[0]").value("¿Cómo retiro dinero a mi banco?"));
    }

    @Test
    void whatTheBotStillDoesNotKnowAfterAskingAnotherWayGoesToAPerson() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "me gusta el futbol los domingos"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOT"))
                .andExpect(jsonPath("$.messages[1].text", containsString("Todavía no tengo información")));

        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "me gusta mucho el futbol de verdad"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void askingAnotherWayStartsOverAfterAnAnswer() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "me gusta el futbol los domingos"}""");
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "como deposito"}""");

        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "me gusta mucho el futbol de verdad"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOT"));
    }

    @Test
    void whatTheBotDoesNotKnowWaitsInTheQueueWithoutAgents() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", UNKNOWN_QUESTION)
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.messages[1].sender").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[1].event").value("ESCALATED"));
    }

    @Test
    void whatTheBotCanOnlyExplainAlsoGoesToAPerson() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "no reconozco un cargo, fue un fraude"}""")
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.messages[1].sender").value("BOT"))
                .andExpect(jsonPath("$.messages[1].asksFeedback").value(false))
                .andExpect(jsonPath("$.messages[2].event").value("ESCALATED"));
    }

    @Test
    void theUserCanAskForAPersonFromTheStart() throws Exception {
        api.postJson(user, CONVERSATION + "/escalate", "").andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void anAgentGetsTheCaseWithItsContextAndANotificationThatLeadsToIt() throws Exception {
        TestUser agent = newAgent();
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "¿Cómo deposito dinero?"}""");

        String id = read(api.postJson(user, CONVERSATION + "/messages", UNKNOWN_QUESTION)
                .andExpect(jsonPath("$.status").value("ASSIGNED")), "$.id");

        assertEventually(() -> assertThat(notifications(agent, "SUPPORT_CASE_ASSIGNED", "referenceId")).contains(id));
        assertEventually(() -> assertThat(notifications(user, "SUPPORT_AGENT_JOINED", "referenceId")).contains(id));
        api.get(agent, AGENT + "/conversations/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Ana López"))
                .andExpect(jsonPath("$.messages[*].sender", hasItem("BOT")))
                .andExpect(jsonPath("$.messages[0].text").value("¿Cómo deposito dinero?"))
                .andExpect(content().string(not(containsString(user.email()))));
        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").value(id));
    }

    @Test
    void anAgentAttendsOneCaseAtATimeAndTheNextComesWhenItCloses() throws Exception {
        TestUser agent = newAgent();
        TestUser second = api.newUser();
        String first = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");
        String next = read(api.postJson(second, CONVERSATION + "/escalate", "")
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1)), "$.id");
        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.queued").value(1));

        api.postJson(agent, AGENT + "/conversations/" + first + "/resolve", "").andExpect(status().isOk());

        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").value(next));
        api.get(second, CONVERSATION).andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void theOneWhoHasWaitedLongestForACaseGetsIt() throws Exception {
        TestUser one = newAgent();
        TestUser other = newAgent();
        String firstCase = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");
        TestUser attended = attends(one, firstCase) ? one : other;
        TestUser waiting = attended == one ? other : one;
        api.postJson(attended, AGENT + "/conversations/" + firstCase + "/resolve", "").andExpect(status().isOk());

        String next = read(api.postJson(api.newUser(), CONVERSATION + "/escalate", ""), "$.id");

        api.get(waiting, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").value(next));
    }

    @Test
    void agentsOnlyReachTheirOwnCases() throws Exception {
        TestUser agent = newAgent();
        String id = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");
        TestUser otherAgent = newAgent();

        api.get(otherAgent, AGENT + "/conversations/" + id).andExpect(status().isNotFound());
        api.postJson(otherAgent, AGENT + "/conversations/" + id + "/messages", """
                {"text": "hola"}""").andExpect(status().isNotFound());
        api.postJson(otherAgent, AGENT + "/conversations/" + id + "/resolve", "").andExpect(status().isNotFound());
        api.get(agent, AGENT + "/conversations/" + id).andExpect(status().isOk());
    }

    @Test
    void usersAndAdministratorsAreNotAgents() throws Exception {
        api.get(user, AGENT + "/summary").andExpect(status().isForbidden());
        api.get(admin, AGENT + "/summary").andExpect(status().isForbidden());
    }

    @Test
    void userAndAgentTalkAndTheSurveyComesAtTheEnd() throws Exception {
        TestUser agent = newAgent();
        api.rename(agent, "Laura Pérez Díaz");
        String id = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");

        api.postJson(agent, AGENT + "/conversations/" + id + "/messages", """
                {"text": "Hola,\\n¿en qué te ayudo?"}""").andExpect(status().isOk());
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "Ya quedó"}""")
                .andExpect(jsonPath("$.agentName").value("Laura"))
                .andExpect(jsonPath("$.userName").doesNotExist())
                .andExpect(jsonPath("$.messages[*].sender", hasItem("AGENT")));
        assertEventually(() -> assertThat(notifications(user, "SUPPORT_AGENT_REPLIED", "referenceId")).contains(id));

        api.postJson(agent, AGENT + "/conversations/" + id + "/resolve", "").andExpect(status().isOk());

        assertEventually(() -> assertThat(notifications(user, "SUPPORT_CASE_RESOLVED", "referenceId")).contains(id));
        api.get(user, CONVERSATION).andExpect(jsonPath("$.awaitsSurvey").value(true));
        api.postJson(user, CONVERSATION + "/survey", """
                {"score": 6}""").andExpect(status().isBadRequest());
        api.postJson(user, CONVERSATION + "/survey", """
                {"score": 5, "comment": "Muy rápido"}""").andExpect(status().isNoContent());
        api.postJson(user, CONVERSATION + "/survey", """
                {"score": 1}""").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUPPORT_SURVEY_NOT_AVAILABLE"));
        api.get(user, CONVERSATION).andExpect(status().isNoContent());
        api.get(admin, "/api/v1/admin/support/conversations/" + id)
                .andExpect(jsonPath("$.surveyScore").value(5))
                .andExpect(jsonPath("$.surveyComment").value("Muy rápido"));
        api.get(admin, "/api/v1/admin/support/summary")
                .andExpect(jsonPath("$.surveys").value(1))
                .andExpect(jsonPath("$.averageScore").value(5.0));
    }

    @Test
    void aConversationClosedWithTheBotHasNoSurveyAndTheNextMessageStartsAnother() throws Exception {
        String first = read(api.postJson(user, CONVERSATION + "/messages", """
                {"text": "¿Cómo deposito dinero?"}"""), "$.id");

        api.postJson(user, CONVERSATION + "/close", "").andExpect(status().isNoContent());

        api.get(user, CONVERSATION).andExpect(status().isNoContent());
        String second = read(api.postJson(user, CONVERSATION + "/messages", """
                {"text": "hola"}"""), "$.id");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void startingAnotherConversationPassesOnTheSurveyOfTheOneBefore() throws Exception {
        newAgent();
        api.postJson(user, CONVERSATION + "/escalate", "");
        api.postJson(user, CONVERSATION + "/close", "").andExpect(jsonPath("$.awaitsSurvey").value(true));

        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "¿Cómo deposito dinero?"}""").andExpect(jsonPath("$.status").value("BOT"));
        api.postJson(user, CONVERSATION + "/close", "").andExpect(status().isNoContent());

        api.get(user, CONVERSATION).andExpect(status().isNoContent());
        api.postJson(user, CONVERSATION + "/survey", """
                {"score": 5}""").andExpect(jsonPath("$.code").value("SUPPORT_SURVEY_NOT_AVAILABLE"));
    }

    @Test
    void thereIsNothingToCloseWithoutAConversation() throws Exception {
        api.postJson(user, CONVERSATION + "/close", "").andExpect(status().isNotFound());
    }

    @Test
    void theUserClosingACaseFreesTheAgentAndStillAsksForTheSurvey() throws Exception {
        TestUser agent = newAgent();
        api.postJson(user, CONVERSATION + "/escalate", "");

        api.postJson(user, CONVERSATION + "/close", "").andExpect(jsonPath("$.awaitsSurvey").value(true));

        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").doesNotExist());
    }

    @Test
    void aFileGoesToAPersonAndOnlyThoseInTheConversationCanDownloadIt() throws Exception {
        TestUser agent = newAgent();
        ResultActions sent = upload(user, CONVERSATION + "/attachments", file("../captura.png", PNG), "Mira esto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.messages[0].text").value("Mira esto"))
                .andExpect(jsonPath("$.messages[0].attachment.fileName").value("captura.png"))
                .andExpect(jsonPath("$.messages[0].attachment.contentType").value("image/png"));
        String attachment = read(sent, "$.messages[0].attachment.id");
        String path = "/api/v1/support/attachments/" + attachment;

        api.get(user, path)
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        api.get(agent, path).andExpect(status().isOk());
        api.get(admin, path).andExpect(status().isOk());
        api.get(api.newUser(), path).andExpect(status().isNotFound());
        api.get(newAgent(), path).andExpect(status().isNotFound());
        api.get(user, "/api/v1/support/attachments/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void refusesFilesThatPretendToBeSomethingElse() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8);

        upload(user, CONVERSATION + "/attachments", file("foto.png", svg), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUPPORT_ATTACHMENT_NOT_ALLOWED"));
    }

    @Test
    void refusesMarkupAndEmptyMessages() throws Exception {
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "<script>alert(1)</script>"}""").andExpect(status().isBadRequest());
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "   "}""").andExpect(status().isBadRequest());
        api.postJson(user, CONVERSATION + "/messages", """
                {"text": "%s"}""".formatted("a".repeat(2001))).andExpect(status().isBadRequest());
    }

    @Test
    void anAgentCanSendAFileButNotWriteOnceClosed() throws Exception {
        TestUser agent = newAgent();
        String id = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");

        upload(agent, AGENT + "/conversations/" + id + "/attachments", file("guia.png", PNG), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[*].sender", hasItem("AGENT")));

        api.postJson(agent, AGENT + "/conversations/" + id + "/resolve", "");
        api.postJson(agent, AGENT + "/conversations/" + id + "/messages", """
                {"text": "hola"}""").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUPPORT_CONVERSATION_CLOSED"));
    }

    @Test
    void someoneCannotAttendTheirOwnCase() throws Exception {
        TestUser agent = newAgent();

        // Agents have no customer chat at all, so they can never be handed their own case
        api.postJson(agent, CONVERSATION + "/escalate", "").andExpect(status().isForbidden());

        api.get(agent, AGENT + "/summary").andExpect(jsonPath("$.activeConversationId").doesNotExist());
    }

    @Test
    void aCaseWaitingWhenAnAgentArrivesGoesToThem() throws Exception {
        api.postJson(user, CONVERSATION + "/escalate", "").andExpect(jsonPath("$.status").value("QUEUED"));

        newAgent();

        assertEventually(() -> api.get(user, CONVERSATION).andExpect(jsonPath("$.status").value("ASSIGNED")));
    }

    @Test
    void aDisabledAgentsCaseGoesBackToTheQueueAndToSomeoneElse() throws Exception {
        TestUser agent = newAgent();
        String id = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");
        TestUser replacement = newAgent();

        api.postJson(admin, "/api/v1/admin/users/" + idOf(agent) + "/disable", """
                {"reason": "Left the team"}""").andExpect(status().isOk());

        assertEventually(() -> api.get(replacement, AGENT + "/summary")
                .andExpect(jsonPath("$.activeConversationId").value(id)));
    }

    @Test
    void anAgentMadeAUserAgainLeavesTheirCaseAndLosesAccess() throws Exception {
        TestUser agent = newAgent();
        api.postJson(user, CONVERSATION + "/escalate", "");

        changeRole(agent, "USER").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));

        assertEventually(() -> api.get(user, CONVERSATION).andExpect(jsonPath("$.status").value("QUEUED")));
        TestUser signedInAgain = api.login(agent.email(), PASSWORD);
        api.get(signedInAgain, AGENT + "/summary").andExpect(status().isForbidden());
    }

    @Test
    void onlyAdministratorsChangeRolesAndNeverTheirOwnKind() throws Exception {
        String path = "/api/v1/admin/users/" + idOf(user) + "/role";

        api.postJson(user, path, """
                {"role": "SUPPORT", "reason": "x"}""").andExpect(status().isForbidden());
        api.postJson(admin, path, """
                {"role": "ADMIN", "reason": "x"}""").andExpect(status().isBadRequest());
        api.postJson(admin, "/api/v1/admin/users/" + idOf(admin) + "/role", """
                {"role": "USER", "reason": "x"}""").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROLE_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void changingARoleEndsTheSessionsAndIsAudited() throws Exception {
        changeRole(user, "SUPPORT").andExpect(status().isOk());

        api.postJson(user, "/api/v1/auth/refresh", """
                {"refreshToken": "%s"}""".formatted(user.refreshToken())).andExpect(status().isUnauthorized());
        assertEventually(() -> assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_events WHERE action = 'USER_ROLE_CHANGED' AND target_id = :id""")
                .param("id", UUID.fromString(idOf(user)))
                .query(Long.class).single()).isEqualTo(1));
    }

    @Test
    void administratorsSeeHowSupportIsDoingButCannotTakePart() throws Exception {
        String id = read(api.postJson(user, CONVERSATION + "/escalate", ""), "$.id");
        api.postJson(api.newUser(), CONVERSATION + "/messages", """
                {"text": "hello"}""");

        api.get(admin, "/api/v1/admin/support/summary")
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.withBot").value(1));
        api.get(admin, "/api/v1/admin/support/conversations?status=QUEUED")
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].userName").value("Ana López"));
        api.get(admin, "/api/v1/admin/support/conversations?status=NOPE").andExpect(status().isBadRequest());
        api.get(user, "/api/v1/admin/support/summary").andExpect(status().isForbidden());
    }

    @Test
    void administratorsSeeWhatTheBotDidWithEachQuestionAndWhetherItHelped() throws Exception {
        ask(user, "¿Cómo deposito dinero?");
        api.postJson(user, CONVERSATION + "/close", "").andExpect(status().isNoContent());
        TestUser second = spanishUser();
        ask(second, "¿Cómo deposito dinero?");
        api.postJson(second, CONVERSATION + "/escalate", "").andExpect(jsonPath("$.status").value("QUEUED"));
        TestUser third = spanishUser();
        ask(third, "hola");
        ask(third, "y en dolares");
        ask(third, "k");
        ask(third, "como pago la tenencia vehicular");

        api.get(admin, "/api/v1/admin/support/bot/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").value(5))
                .andExpect(jsonPath("$.answered").value(2))
                .andExpect(jsonPath("$.helped").value(1))
                .andExpect(jsonPath("$.notHelped").value(1))
                .andExpect(jsonPath("$.suggested").value(1))
                .andExpect(jsonPath("$.askedAgain").value(1))
                .andExpect(jsonPath("$.notFound").value(1))
                .andExpect(jsonPath("$.passedOn").value(0));
    }

    @Test
    void theQuestionsWhereTheBotFailedAreCountedOnceTheMostRepeatedFirst() throws Exception {
        ask(user, "como pago la tenencia vehicular");
        ask(user, "  Como pago la TENENCIA vehicular ");
        TestUser second = spanishUser();
        ask(second, "¿Cómo deposito dinero?");
        api.postJson(second, CONVERSATION + "/escalate", "");
        String third = read(ask(spanishUser(), "k"), "$.id");

        api.get(admin, "/api/v1/admin/support/bot/misses")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].kind").value("NOT_FOUND"))
                .andExpect(jsonPath("$.items[0].times").value(2))
                .andExpect(jsonPath("$.items[0].question").value("Como pago la TENENCIA vehicular"))
                .andExpect(jsonPath("$.items[0].language").value("es"))
                .andExpect(jsonPath("$.items[1].kind").value("NOT_HELPED"))
                .andExpect(jsonPath("$.items[1].topics[0]").value("¿Cómo deposito dinero?"))
                .andExpect(jsonPath("$.hasNext").value(false));
        api.get(admin, "/api/v1/admin/support/bot/misses?kind=ASKED_AGAIN")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].question").value("k"))
                .andExpect(jsonPath("$.items[0].conversationId").value(third));
        api.get(admin, "/api/v1/admin/support/bot/misses?language=en").andExpect(jsonPath("$.items", hasSize(0)));
        api.get(admin, "/api/v1/admin/support/bot/misses?size=1")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void onlyAdministratorsReadWhereTheBotFails() throws Exception {
        api.get(user, "/api/v1/admin/support/bot/misses").andExpect(status().isForbidden());
        api.get(user, "/api/v1/admin/support/bot/summary").andExpect(status().isForbidden());
        api.get(admin, "/api/v1/admin/support/bot/misses?kind=NOPE").andExpect(status().isBadRequest());
        api.get(admin, "/api/v1/admin/support/bot/misses?language=fr").andExpect(status().isBadRequest());
        api.get(admin, "/api/v1/admin/support/bot/summary?days=0").andExpect(status().isBadRequest());
    }

    private ResultActions ask(TestUser account, String text) throws Exception {
        return api.postJson(account, CONVERSATION + "/messages", JsonPath.parse(java.util.Map.of("text", text)).jsonString())
                .andExpect(status().isOk());
    }

    private TestUser spanishUser() throws Exception {
        TestUser account = api.newUser();
        mockMvc.perform(put("/api/v1/users/me/language").header("Authorization", account.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"language": "es"}"""))
                .andExpect(status().isOk());
        return account;
    }

    private TestUser newAgent() throws Exception {
        TestUser account = api.newUser();
        changeRole(account, "SUPPORT").andExpect(status().isOk());
        return api.login(account.email(), PASSWORD);
    }

    private ResultActions changeRole(TestUser account, String role) throws Exception {
        return api.postJson(admin, "/api/v1/admin/users/" + idOf(account) + "/role", """
                {"role": "%s", "reason": "Joined the team"}""".formatted(role));
    }

    private String idOf(TestUser account) throws Exception {
        return read(api.get(account, "/api/v1/users/me"), "$.id");
    }

    private boolean attends(TestUser agent, String conversationId) throws Exception {
        return api.get(agent, AGENT + "/summary").andReturn().getResponse().getContentAsString().contains(conversationId);
    }

    private List<String> notifications(TestUser account, String type, String field) throws Exception {
        String body = api.get(account, "/api/v1/notifications").andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[?(@.type == '%s')].%s".formatted(type, field));
    }

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private ResultActions upload(TestUser account, String path, MockMultipartFile file, String text) throws Exception {
        var request = multipart(path).file(file).header("Authorization", account.bearer());
        return mockMvc.perform(text == null ? request : request.param("text", text));
    }
}
