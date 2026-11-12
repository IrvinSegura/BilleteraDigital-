package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The operation already has a ticket waiting for an administrator: one is enough, and two could be settled twice.
 */
public class TicketAlreadyOpenException extends DomainException {

    public TicketAlreadyOpenException() {
        super("SUPPORT_TICKET_ALREADY_OPEN", "There is already an open support ticket about this");
    }
}
