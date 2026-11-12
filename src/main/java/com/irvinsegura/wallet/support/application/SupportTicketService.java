package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.AccountStanding;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.support.SupportTicketOpened;
import com.irvinsegura.wallet.support.SupportTicketResolved;
import com.irvinsegura.wallet.support.domain.model.ConversationEvent;
import com.irvinsegura.wallet.support.domain.model.ConversationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.OperationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import com.irvinsegura.wallet.support.domain.model.SupportTicket;
import com.irvinsegura.wallet.support.domain.model.TicketAlreadyOpenException;
import com.irvinsegura.wallet.support.domain.model.TicketCategory;
import com.irvinsegura.wallet.support.domain.model.TicketNotFoundException;
import com.irvinsegura.wallet.support.domain.model.TicketStatus;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
import com.irvinsegura.wallet.support.domain.port.SupportTicketRepository;
import com.irvinsegura.wallet.support.domain.port.SupportTicketRepository.TicketSlice;
import com.irvinsegura.wallet.transfers.CustomerOperations;
import com.irvinsegura.wallet.transfers.CustomerOperations.OperationBrief;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Problems support can't fix, in two levels. The agent attending a customer sees their recent operations and where
 * their account stands, read only and while the case is theirs, and passes what needs fixing to the administrators as
 * a ticket tied to the operation, or to the subject when it is about the account.
 * Administrators settle it with the operations tools, which ask for their second factor and keep the audit trail.
 * Whoever sees the customer's case is never whoever moves the money.
 */
@Service
public class SupportTicketService {

    private static final int RECENT_OPERATIONS = 20;

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportTicketRepository tickets;
    private final CustomerOperations operations;
    private final UserDirectory users;
    private final Wallets wallets;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SupportTicketService(SupportConversationRepository conversations, SupportMessageRepository messages,
            SupportTicketRepository tickets, CustomerOperations operations, UserDirectory users, Wallets wallets,
            ApplicationEventPublisher events, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.tickets = tickets;
        this.operations = operations;
        this.users = users;
        this.wallets = wallets;
        this.events = events;
        this.clock = clock;
    }

    /**
     * A ticket with what it is about and the names of the people involved.
     *
     * @param operation the operation as it is now; {@code null} if it can no longer be read
     */
    public record TicketView(SupportTicket ticket, OperationBrief operation, String customerName, String customerEmail,
            String openedByName, String resolvedByName) {
    }

    public record TicketPage(List<TicketView> items, int page, int size, boolean hasNext, long open) {
    }

    /**
     * The customer's recent operations, for the agent attending them. Only while the case is open: once it closes, the
     * agent has no reason to see them.
     *
     * @throws ConversationNotFoundException if it isn't an open case of this agent
     */
    @Transactional(readOnly = true)
    public List<OperationBrief> operations(UUID agentId, UUID conversationId) {
        return operations.recent(openCase(agentId, conversationId).userId(), RECENT_OPERATIONS);
    }

    /**
     * The tickets raised from this case, as its agent follows them.
     */
    @Transactional(readOnly = true)
    public List<TicketView> forCase(UUID agentId, UUID conversationId) {
        conversations.findById(conversationId).filter(found -> agentId.equals(found.agentId()))
                .orElseThrow(ConversationNotFoundException::new);
        return views(tickets.findByConversation(conversationId), false);
    }

    /**
     * Where the customer's account stands, with the state of their wallets: what usually explains why money can't
     * leave.
     *
     * @param wallets the currency of each wallet and whether it is frozen
     */
    public record CustomerStanding(AccountStanding account, List<WalletState> wallets) {
    }

    public record WalletState(String currency, boolean frozen) {
    }

    /**
     * For the agent attending the customer, only while the case is open, as with their operations.
     *
     * @throws ConversationNotFoundException if it isn't an open case of this agent
     */
    @Transactional(readOnly = true)
    public CustomerStanding standing(UUID agentId, UUID conversationId) {
        UUID customer = openCase(agentId, conversationId).userId();
        AccountStanding account = users.findStanding(customer).orElseThrow(ConversationNotFoundException::new);
        return new CustomerStanding(account, wallets.walletsOf(customer).stream()
                .map(wallet -> new WalletState(wallet.currency().getCurrencyCode(), !wallet.active()))
                .sorted(Comparator.comparing(WalletState::currency))
                .toList());
    }

    /**
     * Passes a customer's problem to the administrators, and tells the customer in the chat: about one of their
     * operations, or about the account itself for the other categories.
     *
     * @param operationId the operation, for {@link TicketCategory#OPERATION} only
     * @throws OperationNotFoundException  if the operation isn't the customer's
     * @throws TicketAlreadyOpenException if the operation, or the customer on that subject, already waits for an
     *                                    administrator
     */
    @Transactional
    public TicketView open(UUID agentId, UUID conversationId, TicketCategory category, UUID operationId,
            String description) {
        SupportConversation conversation = conversations.findByIdForUpdate(conversationId)
                .filter(found -> agentId.equals(found.agentId()))
                .orElseThrow(ConversationNotFoundException::new);
        conversation.requireOpen();
        OperationBrief operation = category == TicketCategory.OPERATION
                ? operations.find(conversation.userId(), operationId).orElseThrow(OperationNotFoundException::new)
                : null;
        String kind = operation == null ? null : operation.kind();
        Instant now = clock.instant();
        SupportTicket ticket = SupportTicket.open(conversationId, conversation.userId(), agentId, category, kind,
                operation == null ? null : operationId, description, now);
        if (!tickets.insert(ticket)) {
            throw new TicketAlreadyOpenException();
        }
        messages.add(SupportMessage.event(conversationId, ConversationEvent.TICKET_OPENED, now));
        events.publishEvent(new SupportTicketOpened(ticket.id(), conversationId, conversation.userId(), agentId,
                category.name(), kind, ticket.operationId(), now));
        return view(ticket, operation, false);
    }

    @Transactional(readOnly = true)
    public TicketPage page(TicketStatus status, int page, int size) {
        TicketSlice slice = tickets.page(status, page, size);
        return new TicketPage(views(slice.items(), true), page, size, slice.hasNext(), tickets.countOpen());
    }

    /**
     * What the agent escalated, whatever case it came from and whoever attends it now, so they can follow it until
     * the administrators settle it. With the customer's name only, as in the agent's cases.
     *
     * @param status null for any
     */
    @Transactional(readOnly = true)
    public TicketPage openedBy(UUID agentId, TicketStatus status, int page, int size) {
        TicketSlice slice = tickets.pageOpenedBy(agentId, status, page, size);
        return new TicketPage(views(slice.items(), true), page, size, slice.hasNext(), tickets.countOpenOpenedBy(agentId));
    }

    @Transactional(readOnly = true)
    public TicketView get(UUID ticketId) {
        SupportTicket ticket = tickets.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        return view(ticket, operationOf(ticket), true);
    }

    /**
     * Closes the ticket once the administrator fixed what it was about; the customer is told the resolution.
     *
     * @throws com.irvinsegura.wallet.support.domain.model.TicketClosedException if it was already settled
     */
    @Transactional
    public TicketView resolve(UUID adminId, UUID ticketId, String resolution) {
        SupportTicket ticket = tickets.findByIdForUpdate(ticketId).orElseThrow(TicketNotFoundException::new);
        Instant now = clock.instant();
        ticket.resolve(adminId, resolution, now);
        tickets.update(ticket);
        messages.add(SupportMessage.event(ticket.conversationId(), ConversationEvent.TICKET_RESOLVED, now));
        events.publishEvent(new SupportTicketResolved(ticket.id(), ticket.conversationId(), ticket.userId(), ticket.openedBy(), adminId,
                ticket.resolution(), now));
        return view(ticket, operationOf(ticket), true);
    }

    private OperationBrief operationOf(SupportTicket ticket) {
        return ticket.operationId() == null ? null
                : operations.find(ticket.userId(), ticket.operationId()).orElse(null);
    }

    private SupportConversation openCase(UUID agentId, UUID conversationId) {
        SupportConversation conversation = conversations.findById(conversationId)
                .filter(found -> agentId.equals(found.agentId()))
                .orElseThrow(ConversationNotFoundException::new);
        conversation.requireOpen();
        return conversation;
    }

    private List<TicketView> views(List<SupportTicket> found, boolean forAdmin) {
        Set<UUID> people = new HashSet<>();
        found.forEach(ticket -> {
            people.add(ticket.openedBy());
            if (ticket.resolvedBy() != null) {
                people.add(ticket.resolvedBy());
            }
            if (forAdmin) {
                people.add(ticket.userId());
            }
        });
        Map<UUID, String> names = users.findNames(people);
        return found.stream().map(ticket -> new TicketView(ticket, null,
                forAdmin ? names.get(ticket.userId()) : null, null, names.get(ticket.openedBy()),
                ticket.resolvedBy() == null ? null : names.get(ticket.resolvedBy()))).toList();
    }

    // Agents get the customer's name from the case itself; only administrators see who the customer is in full
    private TicketView view(SupportTicket ticket, OperationBrief operation, boolean forAdmin) {
        Set<UUID> people = new HashSet<>(List.of(ticket.openedBy()));
        if (ticket.resolvedBy() != null) {
            people.add(ticket.resolvedBy());
        }
        Map<UUID, String> names = users.findNames(people);
        UserContact customer = forAdmin ? users.findContact(ticket.userId()).orElse(null) : null;
        return new TicketView(ticket, operation, customer == null ? null : customer.fullName(),
                customer == null ? null : customer.email(), names.get(ticket.openedBy()),
                ticket.resolvedBy() == null ? null : names.get(ticket.resolvedBy()));
    }
}
