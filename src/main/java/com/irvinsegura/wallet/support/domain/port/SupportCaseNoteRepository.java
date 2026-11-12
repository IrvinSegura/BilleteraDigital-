package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.support.domain.model.CaseNote;
import java.util.List;
import java.util.UUID;

public interface SupportCaseNoteRepository {

    void add(CaseNote note);

    /**
     * The notes of the case, the oldest first.
     */
    List<CaseNote> findByConversation(UUID conversationId);
}
