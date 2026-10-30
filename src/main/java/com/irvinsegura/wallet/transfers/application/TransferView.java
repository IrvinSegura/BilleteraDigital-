package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import java.time.Instant;
import java.util.UUID;

public record TransferView(UUID id, UUID sourceWalletId, String recipientEmail, Money amount, String description,
        Instant createdAt) {

    static TransferView from(Transfer transfer) {
        return new TransferView(transfer.id(), transfer.sourceWalletId(), transfer.recipientEmail(),
                transfer.amount(), transfer.description(), transfer.createdAt());
    }
}
