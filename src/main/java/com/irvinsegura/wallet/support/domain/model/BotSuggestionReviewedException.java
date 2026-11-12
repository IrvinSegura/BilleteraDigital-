package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class BotSuggestionReviewedException extends DomainException {

    public BotSuggestionReviewedException() {
        super("SUPPORT_BOT_SUGGESTION_REVIEWED", "Someone already reviewed this suggestion");
    }
}
