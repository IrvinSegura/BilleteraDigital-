package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AttachmentNotFoundException extends DomainException {

    public AttachmentNotFoundException() {
        super("SUPPORT_ATTACHMENT_NOT_FOUND", "File not found");
    }
}
