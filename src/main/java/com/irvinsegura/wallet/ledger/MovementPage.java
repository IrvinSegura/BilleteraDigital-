package com.irvinsegura.wallet.ledger;

import java.util.List;

public record MovementPage(List<AccountMovement> items, int page, int size, boolean hasNext) {
}
