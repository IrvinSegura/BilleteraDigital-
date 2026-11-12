package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AttachmentNotAllowedException extends DomainException {

    public AttachmentNotAllowedException() {
        super("SUPPORT_ATTACHMENT_NOT_ALLOWED", "Only JPEG, PNG or WebP pictures, PDF documents and MP4 videos of up to 20 MB can be sent");
    }
}
