package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;

/**
 * Stands in for a real payout provider (e.g. a SPEI participant such as STP): every payout is paid the moment it is
 * sent, to the CLABE in the order, so a withdrawal completes within the request that makes it. The reference is
 * derived from the withdrawal id, which makes sending the same withdrawal again harmless, as the port requires.
 * <p>
 * No money actually moves, so it is chosen explicitly ({@code wallet.transfers.payouts.provider=simulated}).
 */
class SimulatedPayoutGateway implements PayoutGateway {

    @Override
    public PayoutResult send(PayoutOrder order) {
        return PayoutResult.paid("int_po_" + order.withdrawalId());
    }

    @Override
    public PayoutResult check(String reference) {
        return PayoutResult.paid(reference);
    }
}
