package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import java.time.Instant;
import java.util.UUID;

/**
 * A transfer as one of its two users sees it. {@code sourceWalletId} is only shown to the sender; a name is
 * {@code null} when its user can't be found.
 *
 * @param direction {@code SENT} or {@code RECEIVED}, from the viewer's side
 */
public record TransferView(UUID id, String direction, UUID sourceWalletId, String senderEmail, String senderName,
        String recipientEmail, String recipientName, Money amount, String description, Instant createdAt) {

    static TransferView of(Transfer transfer, UUID viewerId, UserContact sender, UserContact recipient) {
        boolean sent = transfer.senderId().equals(viewerId);
        return new TransferView(transfer.id(), sent ? "SENT" : "RECEIVED", sent ? transfer.sourceWalletId() : null,
                sender == null ? null : sender.email(), sender == null ? null : sender.fullName(),
                transfer.recipientEmail(), recipient == null ? null : recipient.fullName(), transfer.amount(),
                transfer.description(), transfer.createdAt());
    }
}
