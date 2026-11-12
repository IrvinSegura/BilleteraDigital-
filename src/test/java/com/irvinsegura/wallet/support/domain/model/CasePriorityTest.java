package com.irvinsegura.wallet.support.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CasePriorityTest {

    @ParameterizedTest
    @ValueSource(strings = {"No me llegó mi retiro", "Me cobraron dos veces", "mi DEPÓSITO no aparece",
            "Hice una transferencia y no la veo", "Creo que es fraude", "My withdrawal is missing",
            "I was charged twice", "Where is my refund?"})
    void aProblemWithMoneyGoesFirst(String text) {
        assertThat(CasePriority.of(List.of(text))).isEqualTo(CasePriority.HIGH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"¿Cómo cambio mi foto?", "No puedo entrar a mi cuenta", "How do I change my phone?",
            "hola"})
    void anythingElseWaitsItsTurn(String text) {
        assertThat(CasePriority.of(List.of(text))).isEqualTo(CasePriority.NORMAL);
    }

    @Test
    void anyOfWhatTheCustomerWroteCounts() {
        assertThat(CasePriority.of(Arrays.asList("Hola", null, "quiero hablar con alguien de mi saldo")))
                .isEqualTo(CasePriority.HIGH);
        assertThat(CasePriority.of(List.of())).isEqualTo(CasePriority.NORMAL);
    }
}
