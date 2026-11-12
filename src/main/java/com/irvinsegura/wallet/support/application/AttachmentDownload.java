package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.support.domain.model.SupportAttachment;

public record AttachmentDownload(SupportAttachment attachment, byte[] content) {
}
