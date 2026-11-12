package com.irvinsegura.wallet.support.domain.model;

import java.util.Set;

/**
 * How a case that reached a person ended, for the administrators' report of what support does.
 */
public enum CaseResolution {
    // The agent fixed what the customer came with
    SOLVED,
    // It only needed an explanation
    INFORMATION,
    // It went to the administrators as a ticket
    TICKET,
    // The customer stopped answering
    NO_REPLY,
    // The same problem was being handled in another case
    DUPLICATE,
    // The customer closed it themself
    CUSTOMER_CLOSED,
    OTHER;

    /**
     * What an agent can pick when closing a case; the customer closing it is recorded on its own.
     */
    public static final Set<CaseResolution> BY_AGENT = Set.of(SOLVED, INFORMATION, TICKET, NO_REPLY, DUPLICATE, OTHER);
}
