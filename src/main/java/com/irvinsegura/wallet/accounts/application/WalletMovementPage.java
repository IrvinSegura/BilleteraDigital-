package com.irvinsegura.wallet.accounts.application;

import java.util.List;

public record WalletMovementPage(List<WalletMovement> items, int page, int size, boolean hasNext) {
}
