package com.irvinsegura.wallet.accounts;

import java.util.UUID;

/**
 * What a payout needs to reach the user's bank.
 *
 * @param clabe the full 18-digit CLABE
 */
public record BankAccountInfo(UUID id, UUID ownerId, String clabe, String holderName) {
}
