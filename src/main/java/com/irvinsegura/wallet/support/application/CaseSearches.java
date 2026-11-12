package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.CaseSearch;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns what was typed in a search box into the cases it finds: those of the customers it names, and the one whose
 * folio (the beginning of its id, as the screens show it) it is.
 */
final class CaseSearches {

    // A search finds cases, not people: this many customers are plenty for anyone who types a name
    private static final int MAX_CUSTOMERS = 200;
    private static final Pattern FOLIO = Pattern.compile("[0-9a-fA-F-]{4,36}");

    private CaseSearches() {
    }

    /**
     * @param byEmail whether the customer's email counts; agents never see it, so for them it doesn't
     */
    static CaseSearch of(ConversationStatus status, String text, boolean byEmail, UserDirectory users) {
        String typed = text == null ? "" : text.strip();
        if (typed.isEmpty()) {
            return CaseSearch.only(status);
        }
        String folio = FOLIO.matcher(typed).matches() ? typed.toLowerCase(Locale.ROOT) : null;
        List<UUID> customers = users.findIdsMatching(typed, byEmail, MAX_CUSTOMERS);
        return new CaseSearch(status, true, customers, folio);
    }
}
