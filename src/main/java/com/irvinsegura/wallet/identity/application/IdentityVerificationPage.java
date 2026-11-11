package com.irvinsegura.wallet.identity.application;

import java.util.List;

/**
 * @param pending how many wait for review, whatever the page shows; for the menu's counter
 */
public record IdentityVerificationPage(List<IdentityVerificationSummary> items, int page, int size, boolean hasNext,
        long pending) {
}
