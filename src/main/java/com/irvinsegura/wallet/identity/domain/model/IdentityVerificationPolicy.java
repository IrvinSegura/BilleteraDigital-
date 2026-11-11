package com.irvinsegura.wallet.identity.domain.model;

/**
 * @param requiredForWithdrawals whether money can leave for a bank account only once the identity is verified
 * @param maxAttemptsPerDay      verifications a user can send in 24 hours, so nobody can try card after card
 */
public record IdentityVerificationPolicy(boolean requiredForWithdrawals, int maxAttemptsPerDay) {

    public IdentityVerificationPolicy {
        if (maxAttemptsPerDay < 1) {
            throw new IllegalArgumentException("maxAttemptsPerDay must be at least 1");
        }
    }
}
