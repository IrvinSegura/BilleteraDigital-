package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.support.domain.model.ConversationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.ConversationRow;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.ConversationSlice;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Puts together what each kind of reader sees of a conversation, read fresh because assigning it changes it.
 */
@Component
class ConversationViews {

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final UserDirectory users;

    ConversationViews(SupportConversationRepository conversations, SupportMessageRepository messages,
            UserDirectory users) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
    }

    // Users know the agent by first name, and don't need to be told their own
    ConversationView forUser(UUID conversationId) {
        SupportConversation conversation = find(conversationId);
        String agentName = conversation.agentId() == null ? null : users.findNames(Set.of(conversation.agentId()))
                .getOrDefault(conversation.agentId(), "").strip().split("\\s+")[0];
        return new ConversationView(conversation, null, agentName == null || agentName.isEmpty() ? null : agentName,
                position(conversation), messages.findByConversation(conversationId));
    }

    ConversationView forStaff(UUID conversationId) {
        SupportConversation conversation = find(conversationId);
        Map<UUID, String> names = namesOf(Set.of(conversation));
        return new ConversationView(conversation, names.get(conversation.userId()),
                conversation.agentId() == null ? null : names.get(conversation.agentId()), position(conversation),
                messages.findByConversation(conversationId));
    }

    ConversationPage page(ConversationSlice slice, int page, int size) {
        return new ConversationPage(summaries(slice.items()), page, size, slice.hasNext());
    }

    List<ConversationSummary> summaries(List<ConversationRow> rows) {
        Set<SupportConversation> listed = new HashSet<>();
        rows.forEach(row -> listed.add(row.conversation()));
        Map<UUID, String> names = namesOf(listed);
        return rows.stream().map(row -> summary(row, names)).toList();
    }

    private static ConversationSummary summary(ConversationRow row, Map<UUID, String> names) {
        SupportConversation conversation = row.conversation();
        return new ConversationSummary(conversation, names.get(conversation.userId()),
                conversation.agentId() == null ? null : names.get(conversation.agentId()), row.lastText(),
                row.lastMessageAt());
    }

    private Map<UUID, String> namesOf(Set<SupportConversation> listed) {
        Set<UUID> people = new HashSet<>();
        for (SupportConversation conversation : listed) {
            people.add(conversation.userId());
            people.add(conversation.agentId());
        }
        people.removeIf(Objects::isNull);
        return users.findNames(people);
    }

    private Long position(SupportConversation conversation) {
        return conversation.status() == ConversationStatus.QUEUED ? conversations.countAhead(conversation) + 1 : null;
    }

    private SupportConversation find(UUID conversationId) {
        return conversations.findById(conversationId).orElseThrow(ConversationNotFoundException::new);
    }
}
