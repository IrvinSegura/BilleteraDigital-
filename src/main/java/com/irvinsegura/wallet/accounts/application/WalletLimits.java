package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;

/**
 * How much can still leave a wallet today (withdrawals and transfers sent).
 *
 * @param dailyLimit {@code null} when the wallet's currency has no limit; then so is {@code remainingToday}
 * @param resetsAt   when the users' next day starts and the count goes back to zero
 */
public record WalletLimits(Money dailyLimit, Money usedToday, Money remainingToday, Instant resetsAt) {
}
