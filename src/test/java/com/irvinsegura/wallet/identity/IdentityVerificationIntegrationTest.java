package com.irvinsegura.wallet.identity;

import static com.irvinsegura.wallet.support.ApiClient.read;
import static com.irvinsegura.wallet.support.Eventually.assertEventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.support.ApiClient;
import com.irvinsegura.wallet.support.ApiClient.TestUser;
import com.irvinsegura.wallet.support.MailpitContainer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "wallet.identity.verification.required-for-withdrawals=true")
class IdentityVerificationIntegrationTest {

    private static final String ME = "/api/v1/users/me/identity-verification";
    private static final String ADMIN = "/api/v1/admin/identity-verifications";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";
    // Every card of a run is new: the database keeps one account per card
    private static final AtomicInteger NEXT_CARD = new AtomicInteger((int) (System.nanoTime() % 900));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailpitContainer mailpit;

    @Autowired
    private JdbcClient jdbc;

    private ApiClient api;
    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, mailpit);
        user = api.newUser();
        api.rename(user, "Juan Gómez");
    }

    @Test
    void withdrawalsWaitForAnIdentityAnAdministratorApproved() throws Exception {
        String walletId = api.openWalletId(user, "MXN");
        api.deposit(user, walletId, "300.00").andExpect(status().isCreated());
        api.get(user, ME).andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.attemptsLeft").value(3));

        api.withdraw(user, walletId, "50.00").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_NOT_VERIFIED"));

        int card = NEXT_CARD.incrementAndGet();
        String id = read(submit(user, card).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.curpLastFour").value(curp(card).substring(14)))
                .andExpect(jsonPath("$.attemptsLeft").value(2)), "$.id");

        // Nothing of the card is kept in clear, and its pictures are re-encoded without their metadata
        String stored = jdbc.sql("SELECT encrypted_data FROM identity_verifications WHERE id = CAST(:id AS UUID)")
                .param("id", id).query(String.class).single();
        assertThat(stored).doesNotContain(curp(card)).doesNotContain("GOMEZ");

        TestUser admin = api.admin();
        api.get(admin, ADMIN + "?status=PENDING_REVIEW&size=100")
                .andExpect(jsonPath("$.pending").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].nameMatchesAccount").value(true))
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].namesMatchCurp").value(true));
        api.get(admin, ADMIN + "/" + id)
                .andExpect(jsonPath("$.curp").value(curp(card)))
                .andExpect(jsonPath("$.birthDate").value("1990-05-15"))
                .andExpect(jsonPath("$.sex").value("H"));
        byte[] front = api.get(admin, ADMIN + "/" + id + "/images/FRONT")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(front).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
        api.get(user, ADMIN + "/" + id).andExpect(status().isForbidden());

        // The name must stay the one on the card
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\": \"Otro Nombre\"}"))
                .andExpect(jsonPath("$.reason").value("NAME_LOCKED"));
        submit(user, NEXT_CARD.incrementAndGet()).andExpect(jsonPath("$.reason").value("PENDING"));

        api.postJson(admin, ADMIN + "/" + id + "/approve", "").andExpect(jsonPath("$.status").value("VERIFIED"));
        api.postJson(admin, ADMIN + "/" + id + "/reject", "{\"reason\": \"late\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("ALREADY_REVIEWED"));

        api.get(user, ME).andExpect(jsonPath("$.status").value("VERIFIED"));
        api.withdraw(user, walletId, "50.00").andExpect(status().isCreated());
        String userId = read(api.get(user, "/api/v1/users/me"), "$.id");
        assertEventually(() -> assertThat(api.get(admin, "/api/v1/admin/audit-events?targetId=" + userId)
                .andReturn().getResponse().getContentAsString())
                .contains("IDENTITY_VERIFICATION_SUBMITTED", "IDENTITY_VERIFIED"));
        // The customer hears the review ended
        assertEventually(() -> assertThat(api.get(user, "/api/v1/notifications")
                .andReturn().getResponse().getContentAsString())
                .contains("IDENTITY_VERIFIED", "Your identity is verified"));
    }

    @Test
    void aRejectedVerificationSaysWhyAndCanBeSentAgain() throws Exception {
        String id = read(submit(user, NEXT_CARD.incrementAndGet()), "$.id");
        TestUser admin = api.admin();

        api.postJson(admin, ADMIN + "/" + id + "/reject", "{\"reason\": \"La foto del frente está borrosa\"}")
                .andExpect(jsonPath("$.status").value("REJECTED"));

        api.get(user, ME).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("La foto del frente está borrosa"));
        // The notice carries the reason
        assertEventually(() -> assertThat(api.get(user, "/api/v1/notifications")
                .andReturn().getResponse().getContentAsString())
                .contains("IDENTITY_REJECTED", "could not verify it: La foto del frente está borrosa"));
        submit(user, NEXT_CARD.incrementAndGet()).andExpect(status().isCreated());
    }

    @Test
    void aCardBelongsToOneAccount() throws Exception {
        int card = NEXT_CARD.incrementAndGet();
        submit(user, card).andExpect(status().isCreated());
        TestUser other = api.newUser();

        submit(other, card).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("DOCUMENT_IN_USE"));
    }

    @Test
    void wrongDataAndPicturesAreRefusedAtOnce() throws Exception {
        int card = NEXT_CARD.incrementAndGet();
        String badCurp = curp(card).substring(0, 17) + ((curp(card).charAt(17) - '0' + 1) % 10);
        send(user, badCurp, voterKey(card), png(800, 500)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.reason").value("CURP_INVALID"));
        send(user, curp(card), voterKey(card), png(200, 120)).andExpect(jsonPath("$.reason").value("IMAGE_INVALID"));
        send(user, curp(card), voterKey(card), "<svg/>".getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.reason").value("IMAGE_INVALID"));

        // Refused data doesn't count as an attempt
        api.get(user, ME).andExpect(jsonPath("$.attemptsLeft").value(3));
    }

    @Test
    void theCardsDataIsNeverTakenFromTheAddress() throws Exception {
        byte[] picture = png(800, 500);
        mockMvc.perform(multipart(ME + "?curp=" + curp(NEXT_CARD.incrementAndGet()))
                        .file(new MockMultipartFile("front", "front.png", "image/png", picture))
                        .file(new MockMultipartFile("back", "back.png", "image/png", picture))
                        .file(new MockMultipartFile("selfie", "selfie.png", "image/png", picture))
                        .param("firstNames", "Juan").param("firstSurname", "Gómez")
                        .param("voterKey", voterKey(1)).param("cic", "123456789").param("citizenId", "987654321")
                        .param("expiryYear", "2031")
                        .header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest());
        api.get(user, ME).andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    @Test
    void staffCannotVerifyAnIdentity() throws Exception {
        api.get(api.admin(), ME).andExpect(status().isForbidden());
    }

    private ResultActions submit(TestUser who, int card) throws Exception {
        return send(who, curp(card), voterKey(card), png(800, 500));
    }

    private ResultActions send(TestUser who, String curp, String voterKey, byte[] picture) throws Exception {
        return mockMvc.perform(multipart(ME)
                .file(new MockMultipartFile("front", "front.png", "image/png", picture))
                .file(new MockMultipartFile("back", "back.png", "image/png", picture))
                .file(new MockMultipartFile("selfie", "selfie.png", "image/png", picture))
                .param("firstNames", "Juan")
                .param("firstSurname", "Gómez")
                .param("secondSurname", "Martínez")
                .param("curp", curp)
                .param("voterKey", voterKey)
                .param("cic", "123456789")
                .param("citizenId", "987654321")
                .param("expiryYear", "2031")
                .header("Authorization", who.bearer()));
    }

    // Juan Gómez Martínez, born 15 May 1990; the homoclave tells cards apart
    private static String curp(int card) {
        String first17 = "GOMJ900515HDFMR" + "BCDFGHJKLMNPQRSTVWXZ".charAt(card / 10 % 20) + (card % 10);
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += ALPHABET.indexOf(first17.charAt(i)) * (18 - i);
        }
        return first17 + (10 - sum % 10) % 10;
    }

    private static String voterKey(int card) {
        return "GMMRJN90051509H" + "%03d".formatted(card % 1000);
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.PINK);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
