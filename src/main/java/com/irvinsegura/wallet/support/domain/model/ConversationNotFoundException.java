package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class ConversationNotFoundException extends DomainException {

    public ConversationNotFoundException() {
        super("SUPPORT_CONVERSATION_NOT_FOUND", "Support conversation not found");
    }
}
