package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class SavedReplyLimitException extends DomainException {

    public SavedReplyLimitException() {
        super("SUPPORT_SAVED_REPLY_LIMIT", "You can keep up to " + SavedReply.MAX_PER_AGENT + " saved replies");
    }
}
