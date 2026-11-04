package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.ledger.AccountMovement;
import java.time.LocalDate;
import java.util.Set;

/**
 * Which of a wallet's movements a user wants to see. Dates are the users' calendar days, both included; every
 * criterion is optional.
 */
public record MovementCriteria(LocalDate from, LocalDate to, Set<String> types, AccountMovement.Direction direction,
        String referencePrefix) {

    public MovementCriteria {
        types = types == null ? Set.of() : Set.copyOf(types);
    }

    public static MovementCriteria none() {
        return new MovementCriteria(null, null, Set.of(), null, null);
    }
}
