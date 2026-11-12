package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class BotSuggestionNotFoundException extends DomainException {

    public BotSuggestionNotFoundException() {
        super("SUPPORT_BOT_SUGGESTION_NOT_FOUND", "Suggestion not found");
    }
}
