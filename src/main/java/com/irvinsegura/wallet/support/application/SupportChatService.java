package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.AttachmentNotFoundException;
import com.irvinsegura.wallet.support.domain.model.BotFeedback;
import com.irvinsegura.wallet.support.domain.model.BotQuestion;
import com.irvinsegura.wallet.support.domain.model.BotReply;
import com.irvinsegura.wallet.support.domain.model.CasePriority;
import com.irvinsegura.wallet.support.domain.model.CaseResolution;
import com.irvinsegura.wallet.support.domain.model.AttachmentRules;
import com.irvinsegura.wallet.support.domain.model.ConversationEvent;
import com.irvinsegura.wallet.support.domain.model.ConversationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.MessageSender;
import com.irvinsegura.wallet.support.domain.model.SupportAttachment;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import com.irvinsegura.wallet.support.domain.model.SurveyNotAvailableException;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportKnowledge;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chat as a user lives it: every query is scoped to the user id from the access token. The bot answers what it
 * finds in its knowledge base and asks again for what it can't. Problems go to a person, and so does what it still
 * doesn't know after being asked another way.
 */
@Service
public class SupportChatService {

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportKnowledge knowledge;
    private final SupportBotQuestionRepository questions;
    private final ConversationAssignments assignments;
    private final ConversationViews views;
    private final UserDirectory users;
    private final Clock clock;

    SupportChatService(SupportConversationRepository conversations, SupportMessageRepository messages,
            SupportKnowledge knowledge, SupportBotQuestionRepository questions, ConversationAssignments assignments,
            ConversationViews views, UserDirectory users, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.knowledge = knowledge;
        this.questions = questions;
        this.assignments = assignments;
        this.views = views;
        this.clock = clock;
        this.users = users;
    }

    /**
     * The open conversation or, once closed, the one waiting for its survey; empty when the user can start a new one.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationView> current(UUID userId) {
        return conversations.findOpen(userId).or(() -> conversations.findAwaitingSurvey(userId))
                .map(conversation -> views.forUser(conversation.id()));
    }

    /**
     * Adds what the user wrote, starting a conversation if needed, and lets the bot answer while it is still with it.
     */
    @Transactional
    public ConversationView send(UUID userId, String text) {
        lockIfItMayEscalate(userId);
        Instant now = clock.instant();
        SupportConversation conversation = conversations.openForUpdate(userId, now);
        List<SupportMessage> previous = messages.findByConversation(conversation.id());
        SupportMessage question = SupportMessage.fromPerson(conversation.id(), MessageSender.USER, userId, text, null, now);
        messages.add(question);
        if (conversation.status() == ConversationStatus.BOT) {
            Language language = languageOf(userId);
            BotReply reply = knowledge.reply(text, language);
            questions.add(BotQuestion.of(question, reply, language));
            if (reply.unanswered() && alreadySaid(previous, reply)) {
                escalate(conversation, now);
            } else {
                if (reply.text() != null) {
                    messages.add(SupportMessage.fromBot(conversation.id(), reply, now));
                }
                if (reply.escalate()) {
                    escalate(conversation, now);
                }
            }
        }
        return views.forUser(conversation.id());
    }

    /**
     * Adds a file. The bot can't read it, so it also passes the conversation to a person.
     *
     * @throws com.irvinsegura.wallet.support.domain.model.AttachmentNotAllowedException if it can't be sent
     */
    @Transactional
    public ConversationView attach(UUID userId, String fileName, byte[] content, String text) {
        lockIfItMayEscalate(userId);
        Instant now = clock.instant();
        SupportConversation conversation = conversations.openForUpdate(userId, now);
        SupportAttachment attachment = AttachmentRules.describe(conversation.id(), fileName, content);
        messages.addAttachment(attachment, content, now);
        messages.add(SupportMessage.fromPerson(conversation.id(), MessageSender.USER, userId, text, attachment, now));
        escalate(conversation, now);
        return views.forUser(conversation.id());
    }

    /**
     * The user asks for a person, because the answer didn't help or from the start.
     */
    @Transactional
    public ConversationView escalate(UUID userId) {
        lockIfItMayEscalate(userId);
        Instant now = clock.instant();
        SupportConversation conversation = conversations.openForUpdate(userId, now);
        recordFeedbackOnLastAnswer(conversation, BotFeedback.NOT_HELPED);
        escalate(conversation, now);
        return views.forUser(conversation.id());
    }

    /**
     * The user is done, because the answer helped or they no longer need it. If an agent was attending it, they are
     * free for the next case, and the user is asked how it went.
     *
     * @throws ConversationNotFoundException if the user has none open
     */
    @Transactional
    public Optional<ConversationView> close(UUID userId) {
        conversations.lockAssignments();
        SupportConversation conversation = conversations.findOpenForUpdate(userId)
                .orElseThrow(ConversationNotFoundException::new);
        recordFeedbackOnLastAnswer(conversation, BotFeedback.HELPED);
        Instant now = clock.instant();
        // Only what reached a person counts in the report of how cases end
        conversation.resolve(now, conversation.status() == ConversationStatus.BOT ? null : CaseResolution.CUSTOMER_CLOSED);
        conversations.save(conversation);
        messages.add(SupportMessage.event(conversation.id(), ConversationEvent.RESOLVED, now));
        assignments.assignWaiting();
        return conversation.awaitsSurvey() ? Optional.of(views.forUser(conversation.id())) : Optional.empty();
    }

    /**
     * @throws SurveyNotAvailableException if there is no conversation waiting for one
     */
    @Transactional
    public void answerSurvey(UUID userId, int score, String comment) {
        SupportConversation conversation = conversations.findAwaitingSurveyForUpdate(userId)
                .orElseThrow(SurveyNotAvailableException::new);
        conversation.answerSurvey(score, comment, clock.instant());
        conversations.save(conversation);
    }

    /**
     * A file of a conversation the viewer takes part in; others get the same answer as if it didn't exist.
     *
     * @param administrator administrators read every conversation
     * @throws AttachmentNotFoundException if there is none the viewer can see
     */
    @Transactional(readOnly = true)
    public AttachmentDownload attachment(UUID attachmentId, UUID viewerId, boolean administrator) {
        SupportAttachment attachment = messages.findAttachment(attachmentId)
                .orElseThrow(AttachmentNotFoundException::new);
        SupportConversation conversation = conversations.findById(attachment.conversationId())
                .orElseThrow(AttachmentNotFoundException::new);
        if (!administrator && !viewerId.equals(conversation.userId()) && !viewerId.equals(conversation.agentId())) {
            throw new AttachmentNotFoundException();
        }
        return new AttachmentDownload(attachment,
                messages.findAttachmentContent(attachmentId).orElseThrow(AttachmentNotFoundException::new));
    }

    // See ConversationAssignments: a conversation already with people can't escalate, so it doesn't wait for anyone.
    // Checked before locking anything; it only ever moves on from the bot, and then taking the lock costs nothing
    private void lockIfItMayEscalate(UUID userId) {
        if (conversations.findOpen(userId).map(open -> open.status() == ConversationStatus.BOT).orElse(true)) {
            conversations.lockAssignments();
        }
    }

    // Closing or asking for a person right after an answer that asked whether it helped is the user's reply to it
    private void recordFeedbackOnLastAnswer(SupportConversation conversation, BotFeedback feedback) {
        if (conversation.status() != ConversationStatus.BOT) {
            return;
        }
        List<SupportMessage> written = messages.findByConversation(conversation.id());
        if (!written.isEmpty() && written.getLast().sender() == MessageSender.BOT && written.getLast().asksFeedback()) {
            questions.recordFeedback(conversation.id(), feedback);
        }
    }

    // What the customer wrote decides how urgent it is in the queue
    private void escalate(SupportConversation conversation, Instant now) {
        if (conversation.status() != ConversationStatus.BOT) {
            return;
        }
        List<String> written = messages.findByConversation(conversation.id()).stream()
                .filter(message -> message.sender() == MessageSender.USER)
                .map(SupportMessage::text)
                .toList();
        if (conversation.escalate(now, CasePriority.of(written))) {
            conversations.save(conversation);
            messages.add(SupportMessage.event(conversation.id(), ConversationEvent.ESCALATED, now));
            assignments.assignWaiting();
        }
    }

    // Whether the last thing said was this same reply
    private static boolean alreadySaid(List<SupportMessage> previous, BotReply reply) {
        return !previous.isEmpty() && reply.text().equals(previous.getLast().text());
    }

    private Language languageOf(UUID userId) {
        return users.findContact(userId).map(UserContact::language).orElse(Language.DEFAULT);
    }
}
