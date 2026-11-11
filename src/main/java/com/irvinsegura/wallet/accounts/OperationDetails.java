package com.irvinsegura.wallet.accounts;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What only the operations behind a wallet's movements know: who was on the other side and why. The module that runs
 * them depends on this one, so it implements this interface rather than being called directly.
 */
public interface OperationDetails {

    /**
     * The details of those of {@code references} that are operations of the wallet; the others are left out.
     */
    Map<String, OperationDetail> of(UUID walletId, Collection<String> references);

    /**
     * The newest {@code limit} operations of the wallet whose other party's name or description contains
     * {@code text}, ignoring case and accents.
     */
    Set<String> matching(UUID walletId, String text, int limit);

    /**
     * @param counterpartyName the other party's name; {@code null} when their user can't be found
     * @param description      what the sender wrote; {@code null} if nothing
     */
    record OperationDetail(String counterpartyName, String description) {
    }
}
