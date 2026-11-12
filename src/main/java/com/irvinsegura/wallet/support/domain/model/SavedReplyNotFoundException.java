package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class SavedReplyNotFoundException extends DomainException {

    public SavedReplyNotFoundException() {
        super("SUPPORT_SAVED_REPLY_NOT_FOUND", "Saved reply not found");
    }
}
