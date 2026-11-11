package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.OperationDetails;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.transfers.domain.port.WalletTransfers;
import com.irvinsegura.wallet.transfers.domain.port.WalletTransfers.WalletTransfer;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives a wallet's movements the other party and description of their transfers. Callers have already checked who
 * owns the wallet, and only its own transfers are read: both of their users can see these details on the transfer.
 */
@Service
class TransferOperationDetails implements OperationDetails {

    private final WalletTransfers walletTransfers;
    private final UserDirectory users;

    TransferOperationDetails(WalletTransfers walletTransfers, UserDirectory users) {
        this.walletTransfers = walletTransfers;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, OperationDetail> of(UUID walletId, Collection<String> references) {
        List<WalletTransfer> transfers = walletTransfers.find(walletId,
                references.stream().flatMap(reference -> asId(reference).stream()).collect(Collectors.toSet()));
        Map<UUID, String> names = users.findNames(
                transfers.stream().map(WalletTransfer::counterpartyId).collect(Collectors.toSet()));
        Map<String, OperationDetail> details = new HashMap<>();
        for (WalletTransfer transfer : transfers) {
            details.put(transfer.id().toString(),
                    new OperationDetail(names.get(transfer.counterpartyId()), transfer.description()));
        }
        return details;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> matching(UUID walletId, String text, int limit) {
        String folded = fold(text);
        // Names live in another module, so they are compared here, among the only people this wallet can match
        Set<UUID> counterparties = users.findNames(walletTransfers.counterparties(walletId)).entrySet().stream()
                .filter(name -> fold(name.getValue()).contains(folded))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return walletTransfers.findMatching(walletId, folded, counterparties, limit).stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());
    }

    // Lower case and without accents, so "lopez" finds "López"
    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // Other operations' references may not be ids at all
    private static Optional<UUID> asId(String reference) {
        try {
            return Optional.of(UUID.fromString(reference));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
