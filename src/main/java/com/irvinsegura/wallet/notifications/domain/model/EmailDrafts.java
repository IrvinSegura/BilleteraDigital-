package com.irvinsegura.wallet.notifications.domain.model;

import java.util.HashMap;
import java.util.Map;

/**
 * What every email has in common: its wording comes from the keys that start with the reason it is sent, such as
 * {@code deposit-received.title}, and every email is signed the same way.
 */
final class EmailDrafts {

    private EmailDrafts() {
    }

    /**
     * @param greetingValues the values of the greeting
     * @param values         the values of the rest of the texts
     */
    static EmailDraft start(EmailTexts texts, String reason, EmailDraft.Tone tone, String icon,
            Map<String, String> greetingValues, Map<String, String> values) {
        Map<String, String> all = new HashMap<>(values);
        all.putAll(greetingValues);
        EmailDraft draft = new EmailDraft(texts.language(), tone, icon, texts.get(reason + ".kicker"),
                texts.get("brand"), texts.get(reason + ".subject", all), texts.get(reason + ".preheader", all),
                texts.get(reason + ".title", all), texts.get("greeting", all), texts.get("sign-off"));
        draft.paragraphs.addAll(texts.paragraphs(reason + ".body", all));
        draft.calloutTitle = texts.optional(reason + ".callout-title", all);
        draft.callout = texts.optional(reason + ".callout", all);
        draft.footer.add(texts.get("footer.reason"));
        draft.footer.add(texts.get("footer.security"));
        return draft;
    }
}
