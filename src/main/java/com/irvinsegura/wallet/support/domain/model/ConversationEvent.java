package com.irvinsegura.wallet.support.domain.model;

/**
 * What can happen to a conversation, recorded among its messages so everyone reading it can follow what went on.
 */
public enum ConversationEvent {
    ESCALATED,
    ASSIGNED,
    RELEASED,
    // The agent passed the case to a colleague
    TRANSFERRED,
    RESOLVED,
    // An agent passed a problem with the customer's money to the administrators, and when they settled it
    TICKET_OPENED,
    TICKET_RESOLVED,
    // The customer stopped answering the agent: first they are told the case will close, then it is closed
    INACTIVITY_WARNING,
    INACTIVITY_CLOSED
}
