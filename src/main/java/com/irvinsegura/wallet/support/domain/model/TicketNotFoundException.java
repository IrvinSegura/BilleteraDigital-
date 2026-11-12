package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class TicketNotFoundException extends DomainException {

    public TicketNotFoundException() {
        super("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found");
    }
}
