package com.irvinsegura.wallet.accounts;

import static com.irvinsegura.wallet.support.ApiClient.newClabe;
import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class BankAccountIntegrationTest {

    private static final String BANK_ACCOUNTS = "/api/v1/bank-accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    private ApiClient api;
    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        user = api.newUser();
    }

    @Test
    void theFullClabeGoesInButNeverComesBack() throws Exception {
        String clabe = newClabe();

        String id = read(api.addBankAccount(user, clabe)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(BANK_ACCOUNTS + "/")))
                .andExpect(jsonPath("$.bankCode").value("002"))
                .andExpect(jsonPath("$.clabeLastFour").value(clabe.substring(14)))
                .andExpect(jsonPath("$.holderName").value("Ana López"))
                .andExpect(content().string(not(containsString(clabe)))), "$.id");

        api.get(user, BANK_ACCOUNTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(content().string(not(containsString(clabe))));
    }

    @Test
    void rejectsTyposAndDuplicates() throws Exception {
        String clabe = newClabe();
        char last = clabe.charAt(17);
        String typo = clabe.substring(0, 17) + (char) ('0' + (last - '0' + 1) % 10);

        api.addBankAccount(user, typo)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CLABE"));
        api.addBankAccount(user, clabe).andExpect(status().isCreated());
        api.addBankAccount(user, clabe)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_ALREADY_EXISTS"));
        // Another user may hold an account at the same CLABE, e.g. a shared one
        api.addBankAccount(api.newUser(), clabe).andExpect(status().isCreated());
    }

    @Test
    void aDisabledAccountCannotAddOne() throws Exception {
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");
        api.postJson(api.admin(), "/api/v1/admin/users/" + userId + "/disable", """
                {"reason": "Account under review"}""").andExpect(status().isOk());

        // The access token issued before still works for reads, not for this
        api.addBankAccount(user, newClabe())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void atMostFiveAtATime() throws Exception {
        for (int i = 0; i < 5; i++) {
            api.addBankAccount(user, newClabe()).andExpect(status().isCreated());
        }

        api.addBankAccount(user, newClabe())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_LIMIT_REACHED"));
    }

    @Test
    void theLimitHoldsWhenAccountsAreAddedAtOnce() throws Exception {
        for (int i = 0; i < 4; i++) {
            api.addBankAccount(user, newClabe()).andExpect(status().isCreated());
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            List<Callable<Integer>> adds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String clabe = newClabe();
                adds.add(() -> api.addBankAccount(user, clabe).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> add : executor.invokeAll(adds)) {
                statuses.add(add.get());
            }
        }

        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 422);
        api.get(user, BANK_ACCOUNTS).andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void theOwnerNamesAnAccountAndCanClearTheName() throws Exception {
        String id = read(api.addBankAccount(user, newClabe()).andExpect(jsonPath("$.alias").doesNotExist()), "$.id");

        rename(user, id, "\"  Nómina  \"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("Nómina"))
                .andExpect(jsonPath("$.holderName").value("Ana López"));
        api.get(user, BANK_ACCOUNTS).andExpect(jsonPath("$[0].alias").value("Nómina"));

        rename(user, id, "\"<b>x</b>\"").andExpect(status().isBadRequest());
        rename(user, id, "\"" + "x".repeat(41) + "\"").andExpect(status().isBadRequest());
        rename(api.newUser(), id, "\"Mía\"").andExpect(status().isNotFound());

        rename(user, id, "\"\"").andExpect(status().isOk()).andExpect(jsonPath("$.alias").doesNotExist());
        remove(user, id).andExpect(status().isNoContent());
        rename(user, id, "\"Ahorro\"").andExpect(status().isNotFound());
    }

    @Test
    void aRemovedAccountTakesNoNewWithdrawalsButCanBeAddedAgain() throws Exception {
        String clabe = newClabe();
        String id = read(api.addBankAccount(user, clabe), "$.id");
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "100.00").andExpect(status().isCreated());

        remove(api.newUser(), id).andExpect(status().isNotFound());
        remove(user, id).andExpect(status().isNoContent());
        remove(user, id).andExpect(status().isNotFound());

        api.get(user, BANK_ACCOUNTS).andExpect(jsonPath("$.length()").value(0));
        api.withdrawTo(user, walletId, "10.00", newKey(), id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_NOT_FOUND"));
        api.addBankAccount(user, clabe).andExpect(status().isCreated());
    }

    @Test
    void theOwnerIsToldAndSupportCanSeeIt() throws Exception {
        String clabe = newClabe();
        String id = read(api.addBankAccount(user, clabe), "$.id");
        remove(user, id).andExpect(status().isNoContent());

        assertEventually(() -> assertThat(api.mailbox().subjectsFor(user.email())).contains("Bank account added"));
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");
        TestUser admin = api.admin();
        assertEventually(() -> assertThat(auditActions(admin, userId))
                .contains("BANK_ACCOUNT_ADDED", "BANK_ACCOUNT_REMOVED"));
        String audit = api.get(admin, "/api/v1/admin/audit-events?targetId=" + userId)
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains(clabe.substring(14)).doesNotContain(clabe);
    }

    private ResultActions rename(TestUser caller, String bankAccountId, String aliasJson) throws Exception {
        return mockMvc.perform(patch(BANK_ACCOUNTS + "/" + bankAccountId).header("Authorization", caller.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\": " + aliasJson + "}"));
    }

    private ResultActions remove(TestUser caller, String bankAccountId) throws Exception {
        return mockMvc.perform(delete(BANK_ACCOUNTS + "/" + bankAccountId).header("Authorization", caller.bearer()));
    }

    private List<String> auditActions(TestUser admin, String userId) throws Exception {
        return JsonPath.read(api.get(admin, "/api/v1/admin/audit-events?targetId=" + userId)
                .andReturn().getResponse().getContentAsString(), "$.items[*].action");
    }
}
