package com.irvinsegura.wallet.support.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BotSuggestionTest {

    private static final Instant AT = Instant.parse("2026-11-09T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final BotSuggestion suggestion = BotSuggestion.propose(UUID.randomUUID(), UUID.randomUUID(), Language.ES,
            "  ¿Puedo tener dos cuentas?  ", "No, una por persona.", AT);

    @Test
    void startsOpenAndIsReviewedOnce() {
        assertThat(suggestion.status()).isEqualTo(BotSuggestion.Status.OPEN);
        assertThat(suggestion.question()).isEqualTo("¿Puedo tener dos cuentas?");

        suggestion.review(BotSuggestion.Status.ADDED, ADMIN, AT);

        assertThat(suggestion.status()).isEqualTo(BotSuggestion.Status.ADDED);
        assertThat(suggestion.reviewedBy()).isEqualTo(ADMIN);
        assertThatThrownBy(() -> suggestion.review(BotSuggestion.Status.DISMISSED, ADMIN, AT))
                .isInstanceOf(BotSuggestionReviewedException.class);
    }

    @Test
    void aReviewAddsOrDismissesIt() {
        assertThatThrownBy(() -> suggestion.review(BotSuggestion.Status.OPEN, ADMIN, AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void needsAQuestionAndAnAnswer() {
        assertThatThrownBy(() -> BotSuggestion.propose(null, UUID.randomUUID(), Language.EN, " ", "Yes", AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BotSuggestion.propose(null, UUID.randomUUID(), Language.EN, "Why?",
                "x".repeat(BotSuggestion.MAX_ANSWER + 1), AT)).isInstanceOf(IllegalArgumentException.class);
    }
}
