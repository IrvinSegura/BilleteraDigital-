package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;

/**
 * Sends money to the user's bank. Implementations throw
 * {@link com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException} when the provider cannot be
 * reached; the payout is then sent again later.
 */
public interface PayoutGateway {

    /**
     * Idempotent per withdrawal: sending the same withdrawal again returns the payout created the first time, so a
     * retry after a timeout never pays twice.
     */
    PayoutResult send(PayoutOrder order);

    PayoutResult check(String reference);
}
