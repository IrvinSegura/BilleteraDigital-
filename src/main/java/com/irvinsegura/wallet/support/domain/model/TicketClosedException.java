package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class TicketClosedException extends DomainException {

    public TicketClosedException() {
        super("SUPPORT_TICKET_CLOSED", "This support ticket was already resolved");
    }
}
