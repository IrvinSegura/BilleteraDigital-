package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IneCredentialTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    // Juan Gómez Martínez, born 15 May 1990 in Mexico City; check digit 6
    private static final String CURP = "GOMJ900515HDFMRN06";
    private static final String VOTER_KEY = "GMMRJN90051509H100";

    private static IneCredential credential(String curp, String voterKey, int expiryYear) {
        return IneCredential.of("Juan", "Gómez", "Martínez", curp, voterKey, "123456789", "987654321", expiryYear,
                TODAY);
    }

    @Test
    void acceptsAValidCardAndReadsTheBirthDateAndSex() {
        IneCredential credential = credential(" gomj900515hdfmrn06 ", VOTER_KEY, 2030);

        assertThat(credential.curp()).isEqualTo(CURP);
        assertThat(credential.birthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(credential.sex()).isEqualTo('H');
        assertThat(credential.fullName()).isEqualTo("JUAN GÓMEZ MARTÍNEZ");
        assertThat(credential.curpLastFour()).isEqualTo("RN06");
    }

    @Test
    void refusesACurpWithAWrongCheckDigitOrDate() {
        assertReason(() -> credential("GOMJ900515HDFMRN07", VOTER_KEY, 2030), InvalidIdentityDocumentException.CURP_INVALID);
        assertReason(() -> credential("GOMJ900231HDFMRN06", VOTER_KEY, 2030), InvalidIdentityDocumentException.CURP_INVALID);
        assertReason(() -> credential("GOMJ900515HZZMRN06", VOTER_KEY, 2030), InvalidIdentityDocumentException.CURP_INVALID);
    }

    @Test
    void refusesAVoterKeyOfAnotherBirthDateOrSex() {
        assertReason(() -> credential(CURP, "GMMRJN90051609H100", 2030), InvalidIdentityDocumentException.DATA_MISMATCH);
        assertReason(() -> credential(CURP, "GMMRJN90051509M100", 2030), InvalidIdentityDocumentException.DATA_MISMATCH);
        assertReason(() -> credential(CURP, "GMMRJN9005150", 2030), InvalidIdentityDocumentException.VOTER_KEY_INVALID);
    }

    @Test
    void refusesExpiredCardsAndMinors() {
        assertReason(() -> credential(CURP, VOTER_KEY, 2025), InvalidIdentityDocumentException.EXPIRED);
        assertReason(() -> credential(CURP, VOTER_KEY, 2040), InvalidIdentityDocumentException.EXPIRED);
        // Born 1 January 2010 (a letter in the 17th place means the 2000s): 16 years old
        assertReason(() -> IneCredential.of("Ana", "Pérez", null, "PEXA100101MDFRXNA" + checkDigit("PEXA100101MDFRXNA"),
                "PRXXAN10010109M100", "123456789", "987654321", 2030, TODAY), InvalidIdentityDocumentException.UNDERAGE);
    }

    @Test
    void refusesShortCodesOnTheBack() {
        assertReason(() -> IneCredential.of("Juan", "Gómez", "Martínez", CURP, VOTER_KEY, "12345", "987654321", 2030,
                TODAY), InvalidIdentityDocumentException.CIC_INVALID);
        assertReason(() -> IneCredential.of("Juan", "Gómez", "Martínez", CURP, VOTER_KEY, "123456789", "98a654321",
                2030, TODAY), InvalidIdentityDocumentException.CITIZEN_ID_INVALID);
    }

    @Test
    void comparesTheNamesWithTheCurpAndTheAccount() {
        IneCredential credential = credential(CURP, VOTER_KEY, 2030);

        assertThat(credential.namesMatchCurp()).isTrue();
        assertThat(credential.includesName("Juan Gomez")).isTrue();
        assertThat(credential.includesName("juan gómez martínez")).isTrue();
        assertThat(credential.includesName("Pedro Gómez")).isFalse();
        assertThat(IneCredential.of("José Juan", "de la Gómez", "Martínez", CURP, VOTER_KEY, "123456789",
                "987654321", 2030, TODAY).namesMatchCurp()).isTrue();
        assertThat(IneCredential.of("Pedro", "López", null, CURP, VOTER_KEY, "123456789", "987654321", 2030, TODAY)
                .namesMatchCurp()).isFalse();
    }

    private static void assertReason(Runnable action, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(InvalidIdentityDocumentException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private static int checkDigit(String first17) {
        String alphabet = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += alphabet.indexOf(first17.charAt(i)) * (18 - i);
        }
        return (10 - sum % 10) % 10;
    }
}
