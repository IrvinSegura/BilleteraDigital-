package com.irvinsegura.wallet.accounts.application;

import java.util.List;

public record AdminWalletPage(List<AdminWalletView> items, int page, int size, boolean hasNext) {
}
