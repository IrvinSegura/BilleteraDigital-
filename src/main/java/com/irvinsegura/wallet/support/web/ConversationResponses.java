package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.support.application.ConversationPage;
import com.irvinsegura.wallet.support.application.SupportAgentService.NoteView;
import com.irvinsegura.wallet.support.application.ConversationSummary;
import com.irvinsegura.wallet.support.application.ConversationView;
import com.irvinsegura.wallet.support.domain.model.CaseNote;
import com.irvinsegura.wallet.support.domain.model.SupportAttachment;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the controllers of this module share.
 */
final class ConversationResponses {

    static final int MAX_TEXT_LENGTH = 2000;
    // Like PlainText.NO_MARKUP, but a message can have several lines
    static final String MESSAGE_TEXT = "[[^<>\\p{Cntrl}][\\r\\n\\t]]*";
    static final String MESSAGE_TEXT_ERROR = "must not contain <, > or control characters";

    private ConversationResponses() {
    }

    record MessageRequest(@NotBlank @Size(max = MAX_TEXT_LENGTH)
            @Pattern(regexp = MESSAGE_TEXT, message = MESSAGE_TEXT_ERROR) String text) {
    }

    record AttachmentResponse(UUID id, String fileName, String contentType, long sizeBytes) {

        static AttachmentResponse from(SupportAttachment attachment) {
            return attachment == null ? null : new AttachmentResponse(attachment.id(), attachment.fileName(),
                    attachment.contentType(), attachment.sizeBytes());
        }
    }

    /**
     * @param sender       {@code USER}, {@code BOT}, {@code AGENT} or {@code SYSTEM}
     * @param event        for {@code SYSTEM}, what happened: {@code ESCALATED}, {@code ASSIGNED}, {@code RELEASED},
     *                     {@code TRANSFERRED}, {@code RESOLVED}, {@code TICKET_OPENED}, {@code TICKET_RESOLVED},
     *                     {@code INACTIVITY_WARNING} or {@code INACTIVITY_CLOSED}
     * @param asksFeedback an answer of the bot the user can say helped or not
     * @param suggestions  questions the bot offers to pick from; sending one as a message gets its answer
     */
    record MessageResponse(UUID id, String sender, String text, String event, boolean asksFeedback,
            List<String> suggestions, AttachmentResponse attachment, Instant createdAt) {

        static MessageResponse from(SupportMessage message) {
            return new MessageResponse(message.id(), message.sender().name(), message.text(),
                    message.event() == null ? null : message.event().name(), message.asksFeedback(),
                    message.suggestions(), AttachmentResponse.from(message.attachment()), message.createdAt());
        }
    }

    /**
     * @param status        {@code BOT}, {@code QUEUED}, {@code ASSIGNED} or {@code RESOLVED}
     * @param userName      who asked; only for agents and administrators
     * @param agentName     who attends it; users get the first name
     * @param queuePosition its place in the queue, from 1, while it waits
     * @param awaitsSurvey  closed, attended by an agent, and not rated yet
     * @param priority      {@code NORMAL} or {@code HIGH}, for a problem with money
     * @param resolution    how it ended, once closed by or with an agent; {@code null} otherwise
     */
    record ConversationResponse(UUID id, String status, String userName, String agentName, Long queuePosition,
            boolean awaitsSurvey, Instant createdAt, Instant escalatedAt, Instant assignedAt, Instant resolvedAt,
            String priority, String resolution, List<MessageResponse> messages) {

        static ConversationResponse from(ConversationView view) {
            SupportConversation conversation = view.conversation();
            return new ConversationResponse(conversation.id(), conversation.status().name(), view.userName(),
                    view.agentName(), view.queuePosition(), conversation.awaitsSurvey(), conversation.createdAt(),
                    conversation.escalatedAt(), conversation.assignedAt(), conversation.resolvedAt(),
                    conversation.priority().name(), nameOf(conversation.resolution()),
                    view.messages().stream().map(MessageResponse::from).toList());
        }
    }

    /**
     * @param lastText the last thing written in it
     * @param priority {@code NORMAL} or {@code HIGH}, for a problem with money
     */
    record ConversationSummaryResponse(UUID id, String status, String userName, String agentName, String lastText,
            Instant lastMessageAt, Instant createdAt, Instant escalatedAt, Instant assignedAt, Instant resolvedAt,
            String priority, String resolution) {

        static ConversationSummaryResponse from(ConversationSummary summary) {
            SupportConversation conversation = summary.conversation();
            return new ConversationSummaryResponse(conversation.id(), conversation.status().name(), summary.userName(),
                    summary.agentName(), summary.lastText(), summary.lastMessageAt(), conversation.createdAt(),
                    conversation.escalatedAt(), conversation.assignedAt(), conversation.resolvedAt(),
                    conversation.priority().name(), nameOf(conversation.resolution()));
        }

        static List<ConversationSummaryResponse> from(List<ConversationSummary> summaries) {
            return summaries.stream().map(ConversationSummaryResponse::from).toList();
        }
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * A note the team left on a case; the customer never reads it.
     */
    record NoteResponse(UUID id, String authorName, String text, Instant createdAt) {

        static List<NoteResponse> from(List<NoteView> notes) {
            return notes.stream().map(view -> {
                CaseNote note = view.note();
                return new NoteResponse(note.id(), view.authorName(), note.text(), note.createdAt());
            }).toList();
        }
    }

    record ConversationPageResponse(List<ConversationSummaryResponse> items, int page, int size, boolean hasNext) {

        static ConversationPageResponse from(ConversationPage page) {
            return new ConversationPageResponse(page.items().stream().map(ConversationSummaryResponse::from).toList(),
                    page.page(), page.size(), page.hasNext());
        }
    }
}
