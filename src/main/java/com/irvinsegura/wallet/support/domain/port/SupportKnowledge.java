package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.BotReply;

/**
 * What the bot knows about the app, in each language.
 */
public interface SupportKnowledge {

    /**
     * What the bot does with what someone wrote: answer it, ask again, offer what it may have meant or pass it on.
     */
    BotReply reply(String question, Language language);
}
