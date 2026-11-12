package com.irvinsegura.wallet.transfers;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.ledger.application.ReconciliationService;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.FakeStp;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.irvinsegura.wallet.transfers.application.PendingWithdrawalService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * MXN withdrawals paid by SPEI with STP as the payout provider, against {@link FakeStp}: the real HTTP clients, the
 * real signatures and the real database. Pending withdrawals are picked up at once ({@code retry-after=0s}).
 */
@SpringBootTest(properties = {
        "wallet.transfers.payouts.provider=stp",
        "wallet.transfers.payouts.retry-after=0s",
        "wallet.transfers.payouts.stp.company=BILLETERA",
        "wallet.transfers.payouts.stp.account=" + StpPayoutIntegrationTest.PLATFORM_ACCOUNT,
        "wallet.transfers.payouts.stp.account-holder=Billetera Digital SA de CV",
        "wallet.transfers.payouts.stp.account-holder-rfc=BDI200101AB1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StpPayoutIntegrationTest.FakeStpConfiguration.class})
class StpPayoutIntegrationTest {

    static final String PLATFORM_ACCOUNT = "646180110400000007";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private FakeStp stp;

    @Autowired
    private PendingWithdrawalService pendingWithdrawals;

    @Autowired
    private ReconciliationService reconciliation;

    private ApiClient api;
    private TestUser user;
    private String walletId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        user = api.newUser();
        walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "1000.00").andExpect(status().isCreated());
    }

    @AfterEach
    void stpIsBack() {
        stp.setDown(false);
        stp.setOperationDay(null);
    }

    @Test
    void aWithdrawalIsPaidOnceStpSettlesIt() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "300.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");

        JsonNode order = stp.lastOrder();
        String trackingKey = order.path("claveRastreo").asString();
        assertThat(trackingKey).hasSize(29).startsWith("BDW").matches("[A-Z0-9]+");
        assertThat(order.path("empresa").asString()).isEqualTo("BILLETERA");
        assertThat(order.path("cuentaOrdenante").asString()).isEqualTo(PLATFORM_ACCOUNT);
        assertThat(order.path("cuentaBeneficiario").asString()).hasSize(18).startsWith("002");
        // Banamex's CLABE code 002 is SPEI institution 40002
        assertThat(order.path("institucionContraparte").asInt()).isEqualTo(40002);
        assertThat(order.path("institucionOperante").asInt()).isEqualTo(90646);
        assertThat(order.path("monto").decimalValue()).isEqualByComparingTo("300.00");
        // SPEI carries no accents
        assertThat(order.path("nombreBeneficiario").asString()).isEqualTo("Ana Lopez");
        assertThat(order.path("tipoCuentaBeneficiario").asInt()).isEqualTo(40);
        assertThat(order.path("topologia").asString()).isEqualTo("T");
        assertThat(order.path("medioEntrega").asInt()).isEqualTo(3);

        // Still on its way: the job asks and leaves it pending
        pendingWithdrawals.advancePendingWithdrawals();
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PENDING"));

        stp.settle(trackingKey, "LQ", null);
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(api.balance(user, walletId)).isEqualTo("700.00");
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void aPayoutTheBankReturnsGivesTheMoneyBack() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "250.00").andExpect(status().isAccepted()), "$.id");

        stp.settle(stp.lastOrder().path("claveRastreo").asString(), "D", "03");
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("STP_RETURNED_D_03"));
        assertThat(api.balance(user, walletId)).isEqualTo("1000.00");
        assertThat(reconciliation.reconcile(ReconciliationRun.Trigger.MANUAL, null).isConsistent()).isTrue();
    }

    @Test
    void anOrderStpRefusesFailsAndGivesTheMoneyBack() throws Exception {
        stp.rejectNext(-11);

        api.withdraw(user, walletId, "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("STP_REJECTED_11"));

        assertThat(api.balance(user, walletId)).isEqualTo("1000.00");
    }

    @Test
    void aPayoutStpCouldNotTakeStaysPendingInsteadOfGivingTheMoneyBack() throws Exception {
        // Code 0: STP had no answer, or the request itself was wrong (its signature, a missing field)
        stp.rejectNext(0);
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");

        // Sent again on the next run, and paid
        pendingWithdrawals.advancePendingWithdrawals();
        stp.settle(stp.lastOrder().path("claveRastreo").asString(), "LQ", null);
        pendingWithdrawals.advancePendingWithdrawals();
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void anOrderBanxicoRejectsGivesTheMoneyBack() throws Exception {
        String withdrawalId = read(api.withdraw(user, walletId, "100.00").andExpect(status().isAccepted()), "$.id");

        stp.settle(stp.lastOrder().path("claveRastreo").asString(), "RB", null);
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("STP_RETURNED_RB"));
        assertThat(api.balance(user, walletId)).isEqualTo("1000.00");
    }

    @Test
    void aPayoutWhoseAnswerWasLostIsNotRegisteredTwice() throws Exception {
        int before = stp.registrations();
        stp.loseNextAnswer();
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");

        // The job sends it again, finds the order STP already has and only follows it
        pendingWithdrawals.advancePendingWithdrawals();
        stp.settle(stp.lastOrder().path("claveRastreo").asString(), "LQ", null);
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(stp.registrations() - before).isEqualTo(1);
        assertThat(api.balance(user, walletId)).isEqualTo("900.00");
    }

    @Test
    void aPayoutStpDatesOnTheNextOperationDayIsStillFollowed() throws Exception {
        stp.setOperationDay(LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(1));
        String withdrawalId = read(api.withdraw(user, walletId, "100.00").andExpect(status().isAccepted()), "$.id");

        stp.settle(stp.lastOrder().path("claveRastreo").asString(), "LQ", null);
        pendingWithdrawals.advancePendingWithdrawals();

        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void aPayoutIsSentAgainWhenStpWasDown() throws Exception {
        int before = stp.registrations();
        stp.setDown(true);
        String withdrawalId = read(api.withdraw(user, walletId, "100.00")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(stp.registrations()).isEqualTo(before);

        stp.setDown(false);
        pendingWithdrawals.advancePendingWithdrawals();

        assertThat(stp.registrations() - before).isEqualTo(1);
        withdrawal(withdrawalId).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void dollarsCannotBeWithdrawnSinceSpeiPaysOnlyPesos() throws Exception {
        api.get(user, "/api/v1/withdrawals/currencies").andExpect(jsonPath("$.currencies.length()").value(1))
                .andExpect(jsonPath("$.currencies[0]").value("MXN"));
        String dollars = api.openWalletId(user, "USD");
        api.deposit(user, dollars, "100.00").andExpect(status().isCreated());
        int before = stp.registrations();

        api.withdraw(user, dollars, "40.00").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_CURRENCY_NOT_SUPPORTED"));

        assertThat(api.balance(user, dollars)).isEqualTo("100.00");
        assertThat(stp.registrations()).isEqualTo(before);
    }

    private ResultActions withdrawal(String withdrawalId) throws Exception {
        return api.get(user, "/api/v1/wallets/" + walletId + "/withdrawals/" + withdrawalId)
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeStpConfiguration {

        @Bean
        FakeStp fakeStp() {
            return new FakeStp();
        }

        @Bean
        DynamicPropertyRegistrar stpProperties(FakeStp stp) {
            return registry -> {
                registry.add("wallet.transfers.payouts.stp.orders-url", stp::url);
                registry.add("wallet.transfers.payouts.stp.query-url", stp::url);
                registry.add("wallet.transfers.payouts.stp.private-key", stp::privateKeyPem);
            };
        }
    }
}
