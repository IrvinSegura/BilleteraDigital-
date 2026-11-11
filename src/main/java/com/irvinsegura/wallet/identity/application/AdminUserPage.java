package com.irvinsegura.wallet.identity.application;

import java.util.List;

public record AdminUserPage(List<AdminUserSummary> items, int page, int size, boolean hasNext) {
}
