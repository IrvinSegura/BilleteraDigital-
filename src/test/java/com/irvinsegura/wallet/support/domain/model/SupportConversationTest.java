package com.irvinsegura.wallet.support.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportConversationTest {

    private static final Instant AT = Instant.parse("2026-11-05T10:00:00Z");
    private static final UUID AGENT = UUID.randomUUID();

    private final SupportConversation conversation = SupportConversation.start(UUID.randomUUID(), AT);

    @Test
    void startsWithTheBot() {
        assertThat(conversation.status()).isEqualTo(ConversationStatus.BOT);
        assertThat(conversation.awaitsSurvey()).isFalse();
    }

    @Test
    void escalatesOnlyOnceAndKeepsItsPlaceInTheQueue() {
        assertThat(conversation.escalate(AT, CasePriority.NORMAL)).isTrue();
        assertThat(conversation.escalate(AT.plusSeconds(60), CasePriority.HIGH)).isFalse();

        assertThat(conversation.status()).isEqualTo(ConversationStatus.QUEUED);
        assertThat(conversation.escalatedAt()).isEqualTo(AT);
        assertThat(conversation.priority()).isEqualTo(CasePriority.NORMAL);
    }

    @Test
    void onlyAConversationInTheQueueCanBeAssigned() {
        assertThatThrownBy(() -> conversation.assignTo(AGENT, AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void goesBackToTheQueueWhenItsAgentCanNoLongerAttendIt() {
        conversation.escalate(AT, CasePriority.NORMAL);
        conversation.assignTo(AGENT, AT);

        conversation.release();

        assertThat(conversation.status()).isEqualTo(ConversationStatus.QUEUED);
        assertThat(conversation.agentId()).isNull();
        assertThat(conversation.escalatedAt()).isEqualTo(AT);
    }

    @Test
    void cannotBeClosedTwice() {
        conversation.resolve(AT, CaseResolution.SOLVED);

        assertThat(conversation.resolution()).isEqualTo(CaseResolution.SOLVED);
        assertThatThrownBy(() -> conversation.resolve(AT, CaseResolution.OTHER))
                .isInstanceOf(ConversationClosedException.class);
        assertThat(conversation.resolution()).isEqualTo(CaseResolution.SOLVED);
    }

    @Test
    void onlyAConversationAnAgentAttendedAsksForASurvey() {
        conversation.resolve(AT, CaseResolution.SOLVED);

        assertThat(conversation.awaitsSurvey()).isFalse();
        assertThatThrownBy(() -> conversation.answerSurvey(5, null, AT)).isInstanceOf(SurveyNotAvailableException.class);
    }

    @Test
    void takesOneSurvey() {
        conversation.escalate(AT, CasePriority.NORMAL);
        conversation.assignTo(AGENT, AT);
        conversation.resolve(AT, CaseResolution.SOLVED);
        assertThat(conversation.awaitsSurvey()).isTrue();

        conversation.answerSurvey(4, "  Fast  ", AT);

        assertThat(conversation.surveyScore()).isEqualTo(4);
        assertThat(conversation.surveyComment()).isEqualTo("Fast");
        assertThat(conversation.awaitsSurvey()).isFalse();
        assertThatThrownBy(() -> conversation.answerSurvey(1, null, AT)).isInstanceOf(SurveyNotAvailableException.class);
    }
}
