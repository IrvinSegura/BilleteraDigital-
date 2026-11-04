package com.irvinsegura.wallet.accounts;

import static com.irvinsegura.wallet.support.ApiClient.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WalletSummaryIntegrationTest {

    // wallet.accounts.limit-zone, which sets when the users' days and months start
    private static final ZoneId USERS_ZONE = ZoneId.of("America/Mexico_City");

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
    void monthSummaryAddsUpEveryMovementOfTheMonth() throws Exception {
        TestUser user = api.newUser();
        TestUser friend = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.openWalletId(friend, "MXN");
        api.deposit(user, walletId, "100.50").andExpect(status().isCreated());
        api.deposit(user, walletId, "20").andExpect(status().isCreated());
        api.transfer(user, walletId, friend.email(), "30.25", newKey()).andExpect(status().isCreated());

        api.get(user, "/api/v1/wallets/" + walletId + "/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now(USERS_ZONE).toString()))
                .andExpect(jsonPath("$.openingBalance.amount").value("0.00"))
                .andExpect(jsonPath("$.closingBalance.amount").value("90.25"))
                .andExpect(jsonPath("$.moneyIn.amount").value("120.50"))
                .andExpect(jsonPath("$.moneyIn.currency").value("MXN"))
                .andExpect(jsonPath("$.moneyOut.amount").value("30.25"));

        // Another month had nothing
        api.get(user, "/api/v1/wallets/" + walletId + "/summary?month="
                        + YearMonth.now(USERS_ZONE).minusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyIn.amount").value("0.00"))
                .andExpect(jsonPath("$.moneyOut.amount").value("0.00"))
                .andExpect(jsonPath("$.closingBalance.amount").value("0.00"));

        // Next month opens with what this one ended with
        api.get(user, "/api/v1/wallets/" + walletId + "/summary?month="
                        + YearMonth.now(USERS_ZONE).plusMonths(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance.amount").value("90.25"));
    }

    @Test
    void movementsCanBeFilteredByPeriodTypeDirectionAndOperation() throws Exception {
        TestUser user = api.newUser();
        TestUser friend = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.openWalletId(friend, "MXN");
        api.deposit(user, walletId, "100.50").andExpect(status().isCreated());
        api.deposit(user, walletId, "20").andExpect(status().isCreated());
        api.transfer(user, walletId, friend.email(), "30.25", newKey()).andExpect(status().isCreated());
        String movements = "/api/v1/wallets/" + walletId + "/movements";
        LocalDate today = LocalDate.now(USERS_ZONE);

        api.get(user, movements + "?direction=OUT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER"));
        api.get(user, movements + "?type=DEPOSIT&type=WITHDRAWAL")
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].amount.amount").value("20.00"));
        api.get(user, movements + "?from=" + today + "&to=" + today).andExpect(jsonPath("$.items", hasSize(3)));
        api.get(user, movements + "?to=" + today.minusDays(1)).andExpect(jsonPath("$.items", hasSize(0)));
        api.get(user, movements + "?from=" + today.plusDays(1)).andExpect(jsonPath("$.items", hasSize(0)));

        String transferId = ApiClient.read(api.get(user, movements + "?direction=OUT"), "$.items[0].operationId");
        api.get(user, movements + "?q=" + transferId.substring(0, 8).toUpperCase())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].operationId").value(transferId));
    }

    @Test
    void oneMovementIsFoundByItsTransactionForItsWalletOnly() throws Exception {
        TestUser user = api.newUser();
        TestUser friend = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        String friendWallet = api.openWalletId(friend, "MXN");
        api.deposit(user, walletId, "50").andExpect(status().isCreated());
        api.transfer(user, walletId, friend.email(), "12.30", newKey()).andExpect(status().isCreated());
        String transactionId = ApiClient.read(api.get(user, "/api/v1/wallets/" + walletId + "/movements"),
                "$.items[0].transactionId");

        api.get(user, "/api/v1/wallets/" + walletId + "/movements/" + transactionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.amount.amount").value("12.30"))
                .andExpect(jsonPath("$.balanceAfter.amount").value("37.70"));
        // The same transaction seen from the wallet that received it
        api.get(friend, "/api/v1/wallets/" + friendWallet + "/movements/" + transactionId)
                .andExpect(jsonPath("$.direction").value("IN"));
        // Not a movement of this wallet, or not this user's wallet
        api.get(user, "/api/v1/wallets/" + walletId + "/movements/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVEMENT_NOT_FOUND"));
        api.get(user, "/api/v1/wallets/" + friendWallet + "/movements/" + transactionId)
                .andExpect(status().isNotFound());
    }

    @Test
    void limitsShowWhatCanStillLeaveTheWalletToday() throws Exception {
        TestUser user = api.newUser();
        TestUser friend = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.openWalletId(friend, "MXN");
        api.deposit(user, walletId, "100").andExpect(status().isCreated());
        api.transfer(user, walletId, friend.email(), "30.25", newKey()).andExpect(status().isCreated());

        // application-test.yml caps MXN at 500.00 a day
        api.get(user, "/api/v1/wallets/" + walletId + "/limits")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyOutgoingLimit.amount").value("500.00"))
                .andExpect(jsonPath("$.usedToday.amount").value("30.25"))
                .andExpect(jsonPath("$.remainingToday.amount").value("469.75"))
                .andExpect(jsonPath("$.resetsAt").value(LocalDate.now(USERS_ZONE).plusDays(1)
                        .atStartOfDay(USERS_ZONE).toInstant().toString()));
        api.get(friend, "/api/v1/wallets/" + walletId + "/limits").andExpect(status().isNotFound());
    }

    @Test
    void statementIsACsvOfThePeriodOldestFirst() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "100.50").andExpect(status().isCreated());
        api.deposit(user, walletId, "20").andExpect(status().isCreated());
        LocalDate today = LocalDate.now(USERS_ZONE);

        String csv = api.get(user, "/api/v1/wallets/" + walletId + "/statement?from=" + today.minusDays(30)
                        + "&to=" + today)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("statement-" + today.minusDays(30) + "-" + today + ".csv")))
                .andReturn().getResponse().getContentAsString();

        List<String> lines = csv.lines().toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("posted_at,type,direction,amount,currency,balance_after,reference");
        assertThat(lines.get(1)).startsWith(today.toString()).contains(",DEPOSIT,IN,100.50,MXN,100.50,");
        assertThat(lines.get(2)).contains(",DEPOSIT,IN,20.00,MXN,120.50,");
    }

    @Test
    void balanceHistoryStartsTheDayTheWalletWasOpened() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "75").andExpect(status().isCreated());

        api.get(user, "/api/v1/wallets/" + walletId + "/balance-history?days=30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance.amount").value("0.00"))
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].date").value(LocalDate.now(USERS_ZONE).toString()))
                .andExpect(jsonPath("$.points[0].balance.amount").value("75.00"))
                .andExpect(jsonPath("$.points[0].balance.currency").value("MXN"));
    }

    @Test
    void rejectsBadPeriods() throws Exception {
        TestUser user = api.newUser();
        String walletId = api.openWalletId(user, "MXN");

        api.get(user, "/api/v1/wallets/" + walletId + "/summary?month=2026-13").andExpect(status().isBadRequest());
        api.get(user, "/api/v1/wallets/" + walletId + "/summary?month=nov").andExpect(status().isBadRequest());
        api.get(user, "/api/v1/wallets/" + walletId + "/balance-history?days=0").andExpect(status().isBadRequest());
        api.get(user, "/api/v1/wallets/" + walletId + "/balance-history?days=367")
                .andExpect(status().isBadRequest());
        String movements = "/api/v1/wallets/" + walletId + "/movements";
        api.get(user, movements + "?from=2026-11-02&to=2026-11-01").andExpect(status().isBadRequest());
        api.get(user, movements + "?from=yesterday").andExpect(status().isBadRequest());
        api.get(user, movements + "?direction=SIDEWAYS").andExpect(status().isBadRequest());
        api.get(user, movements + "?type=deposit").andExpect(status().isBadRequest());
        api.get(user, movements + "?q=50%25").andExpect(status().isBadRequest());
        String statement = "/api/v1/wallets/" + walletId + "/statement";
        api.get(user, statement + "?from=2026-01-01").andExpect(status().isBadRequest());
        api.get(user, statement + "?from=2025-01-01&to=2026-01-02").andExpect(status().isBadRequest());
    }

    @Test
    void someoneElsesWalletIsNotFound() throws Exception {
        TestUser owner = api.newUser();
        TestUser stranger = api.newUser();
        String walletId = api.openWalletId(owner, "MXN");

        api.get(stranger, "/api/v1/wallets/" + walletId + "/summary").andExpect(status().isNotFound());
        api.get(stranger, "/api/v1/wallets/" + walletId + "/balance-history").andExpect(status().isNotFound());
        api.get(stranger, "/api/v1/wallets/" + walletId + "/movements?direction=IN")
                .andExpect(status().isNotFound());
        api.get(stranger, "/api/v1/wallets/" + walletId + "/statement?from=2026-01-01&to=2026-01-31")
                .andExpect(status().isNotFound());
    }
}
