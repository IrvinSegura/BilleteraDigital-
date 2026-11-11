package com.irvinsegura.wallet.transfers.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A wallet's transfers, sent and received, seen from that wallet.
 */
public interface WalletTransfers {

    /**
     * Those of {@code ids} that moved money in or out of the wallet.
     */
    List<WalletTransfer> find(UUID walletId, Collection<UUID> ids);

    /**
     * Everyone the wallet has sent money to or received it from.
     */
    Set<UUID> counterparties(UUID walletId);

    /**
     * The newest transfers of the wallet whose description contains {@code foldedText} (lower case, no accents,
     * matched literally) or whose other party is one of {@code counterparties}.
     */
    List<UUID> findMatching(UUID walletId, String foldedText, Collection<UUID> counterparties, int limit);

    record WalletTransfer(UUID id, UUID counterpartyId, String description) {
    }
}
