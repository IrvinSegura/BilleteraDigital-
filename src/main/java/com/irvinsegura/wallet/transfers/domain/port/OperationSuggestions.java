package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.OperationSuggestion;
import java.util.List;

/**
 * The read behind the support search's preview of operations.
 */
public interface OperationSuggestions {

    /**
     * The newest operations whose id starts with the text.
     *
     * @param idStart lower case hex digits and dashes
     * @param kind    {@code transfer}, {@code deposit} or {@code withdrawal}; null for any
     */
    List<OperationSuggestion> find(String idStart, String kind, int limit);
}
