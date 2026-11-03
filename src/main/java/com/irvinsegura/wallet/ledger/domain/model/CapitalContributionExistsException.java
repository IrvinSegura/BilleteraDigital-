package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class CapitalContributionExistsException extends DomainException {

    public CapitalContributionExistsException() {
        super("CAPITAL_CONTRIBUTION_EXISTS", "A capital contribution with this bank reference was already recorded");
    }
}
