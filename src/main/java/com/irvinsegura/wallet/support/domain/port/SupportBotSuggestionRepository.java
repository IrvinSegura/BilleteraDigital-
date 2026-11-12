package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.support.domain.model.BotSuggestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportBotSuggestionRepository {

    void insert(BotSuggestion suggestion);

    void update(BotSuggestion suggestion);

    Optional<BotSuggestion> findByIdForUpdate(UUID id);

    /**
     * Open ones the oldest first, so none waits forever; reviewed ones the most recent first.
     *
     * @param status {@code null} for any
     */
    SuggestionSlice page(BotSuggestion.Status status, int page, int size);

    long countOpen();

    record SuggestionSlice(List<BotSuggestion> items, boolean hasNext) {
    }
}
