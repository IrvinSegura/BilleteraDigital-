package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.PASSWORD;
import static com.irvinsegura.wallet.support.ApiClient.newClabe;
import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.identity.domain.model.Totp;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
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
class TwoFactorIntegrationTest {

    private static final String TWO_FACTOR = "/api/v1/users/me/two-factor";

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
    void signingInAsksForTheAppOnceItIsConfirmed() throws Exception {
        api.postJson(user, TWO_FACTOR, """
                {"currentPassword": "wrong password"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCORRECT_PASSWORD"));
        String secret = read(api.postJson(user, TWO_FACTOR, passwordOnly())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/Digital%20Wallet:"))), "$.secret");
        // Not on until confirmed, so a setup abandoned halfway never locks anyone out
        login(null).andExpect(status().isOk());

        Instant now = Instant.now();
        api.postJson(user, TWO_FACTOR + "/confirm", code("000000")).andExpect(status().isUnprocessableContent());
        List<String> recoveryCodes = JsonPath.read(api.postJson(user, TWO_FACTOR + "/confirm",
                code(Totp.codeAt(secret, now))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), "$.recoveryCodes");
        assertThat(recoveryCodes).hasSize(10).doesNotHaveDuplicates();
        // Every session ended, so one stolen before has no way to keep going without the app
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("""
                {"refreshToken": "%s"}""".formatted(user.refreshToken()))).andExpect(status().isUnauthorized());

        login(null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));
        login("000000").andExpect(status().isUnauthorized());
        String nextCode = Totp.codeAt(secret, now.plusSeconds(30));
        login(nextCode).andExpect(status().isOk());
        // A code works once, so one read over someone's shoulder is worthless afterwards
        login(nextCode).andExpect(status().isUnauthorized());
        login(recoveryCodes.getFirst()).andExpect(status().isOk());
        login(recoveryCodes.getFirst()).andExpect(status().isUnauthorized());

        api.get(user, TWO_FACTOR)
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.unusedRecoveryCodes").value(9));
        api.get(user, "/api/v1/users/me").andExpect(jsonPath("$.twoFactorEnabled").value(true));
        assertEventually(() -> assertThat(api.mailbox().subjectsFor(user.email()))
                .contains("Two-factor authentication is on"));
    }

    @Test
    void addingABankAccountNeedsTheSecondFactor() throws Exception {
        List<String> recoveryCodes = enableTwoFactor();

        api.addBankAccount(user, newClabe())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));
        addBankAccount("aaaaa-aaaaa")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        addBankAccount(recoveryCodes.get(1)).andExpect(status().isCreated());
        addBankAccount(recoveryCodes.get(1)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void wrongCodesLockTheAccountLikeWrongPasswords() throws Exception {
        enableTwoFactor();

        for (int attempt = 0; attempt < 5; attempt++) {
            login("aaaaa-aaaaa").andExpect(status().isUnauthorized());
        }

        login("bbbbb-bbbbb").andExpect(status().isLocked());
        addBankAccount("bbbbb-bbbbb").andExpect(status().isLocked());
    }

    @Test
    void turningItOffNeedsThePasswordAndASecondFactor() throws Exception {
        List<String> recoveryCodes = enableTwoFactor();

        api.postJson(user, TWO_FACTOR + "/disable", """
                {"currentPassword": "%s", "code": "aaaaa-aaaaa"}""".formatted(PASSWORD))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));
        api.postJson(user, TWO_FACTOR + "/disable", """
                {"currentPassword": "%s", "code": "%s"}""".formatted(PASSWORD, recoveryCodes.get(2)))
                .andExpect(status().isNoContent());

        login(null).andExpect(status().isOk());
        assertEventually(() -> assertThat(api.mailbox().subjectsFor(user.email()))
                .contains("Two-factor authentication is off"));
    }

    @Test
    void supportCanTurnItOffForSomeoneWhoLostTheirPhone() throws Exception {
        enableTwoFactor();
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");
        TestUser admin = api.admin();

        api.postJson(user, "/api/v1/admin/users/" + userId + "/two-factor/reset", reason())
                .andExpect(status().isForbidden());
        api.postJson(admin, "/api/v1/admin/users/" + userId + "/two-factor/reset", reason())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasEnabled").value(true));

        login(null).andExpect(status().isOk());
        assertEventually(() -> assertThat(JsonPath.<List<String>>read(api.get(admin,
                        "/api/v1/admin/audit-events?targetId=" + userId).andReturn().getResponse()
                        .getContentAsString(), "$.items[*].action"))
                .contains("TWO_FACTOR_ENABLED", "TWO_FACTOR_DISABLED"));
    }

    private List<String> enableTwoFactor() throws Exception {
        String secret = read(api.postJson(user, TWO_FACTOR, passwordOnly()), "$.secret");
        return JsonPath.read(api.postJson(user, TWO_FACTOR + "/confirm", code(Totp.codeAt(secret, Instant.now())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.recoveryCodes");
    }

    private ResultActions login(String twoFactorCode) throws Exception {
        String code = twoFactorCode == null ? "" : ", \"twoFactorCode\": \"%s\"".formatted(twoFactorCode);
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"%s}""".formatted(user.email(), PASSWORD, code)));
    }

    private ResultActions addBankAccount(String twoFactorCode) throws Exception {
        return api.postJson(user, "/api/v1/bank-accounts", """
                {"clabe": "%s", "holderName": "Ana López", "twoFactorCode": "%s"}"""
                .formatted(newClabe(), twoFactorCode));
    }

    private static String passwordOnly() {
        return """
                {"currentPassword": "%s"}""".formatted(PASSWORD);
    }

    private static String code(String code) {
        return """
                {"code": "%s"}""".formatted(code);
    }

    private static String reason() {
        return """
                {"reason": "Lost phone, identity confirmed by video call"}""";
    }
}
