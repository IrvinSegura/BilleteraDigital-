package com.irvinsegura.wallet.ledger.domain.model;

/**
 * Accounting type, which decides the side that increases the balance (the normal side).
 */
public enum AccountType {
    // What the platform holds, e.g. cash at the partner bank
    ASSET(Side.DEBIT),
    // What the platform owes, e.g. user wallet balances
    LIABILITY(Side.CREDIT);

    private final Side normalSide;

    AccountType(Side normalSide) {
        this.normalSide = normalSide;
    }

    public boolean increasesWith(Side side) {
        return side == normalSide;
    }
}
