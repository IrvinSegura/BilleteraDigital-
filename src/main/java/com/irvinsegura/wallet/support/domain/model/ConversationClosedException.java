package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class ConversationClosedException extends DomainException {

    public ConversationClosedException() {
        super("SUPPORT_CONVERSATION_CLOSED", "This support conversation is closed");
    }
}
