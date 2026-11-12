package com.irvinsegura.wallet.support.application;

import java.util.List;

public record ConversationPage(List<ConversationSummary> items, int page, int size, boolean hasNext) {
}
