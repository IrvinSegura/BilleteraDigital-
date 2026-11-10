package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.util.ArrayList;
import java.util.List;

/**
 * What one email says, before {@link EmailLayout} gives it its HTML and plain-text form. Every value is plain text,
 * including whatever users wrote such as their names: the layout escapes it.
 */
final class EmailDraft {

    /**
     * The mood of the email, which sets its colors.
     */
    enum Tone {
        SUCCESS,
        INFO,
        WARNING,
        DANGER
    }

    /**
     * One line of the receipt.
     *
     * @param code whether the value is a reference to read character by character, such as a folio, and is set in
     *             a monospaced font
     */
    record Detail(String label, String value, boolean code) {

        Detail(String label, String value) {
            this(label, value, false);
        }
    }

    record Action(String label, String url) {
    }

    final Language language;
    final Tone tone;
    final String icon;
    final String kicker;
    final String subject;
    final String preheader;
    final String title;
    final String greeting;
    final String brand;
    final String signOff;
    final List<String> paragraphs = new ArrayList<>();
    final List<Detail> details = new ArrayList<>();
    final List<String> footer = new ArrayList<>();
    String amountLabel;
    String amount;
    Action action;
    Action secondaryAction;
    String linkHint;
    String calloutTitle;
    String callout;
    Action footerLink;

    /**
     * @param icon    a typographic mark, never an emoji: emoji look different in every client and some show them as
     *                empty boxes
     * @param kicker  the short label above the title that says what kind of email it is
     * @param signOff who signs the email
     */
    EmailDraft(Language language, Tone tone, String icon, String kicker, String brand, String subject, String preheader,
            String title, String greeting, String signOff) {
        this.language = language;
        this.tone = tone;
        this.icon = icon;
        this.kicker = kicker;
        this.brand = brand;
        this.signOff = signOff;
        this.subject = subject;
        this.preheader = preheader;
        this.title = title;
        this.greeting = greeting;
    }

    EmailDraft detail(String label, String value) {
        details.add(new Detail(label, value));
        return this;
    }

    EmailDraft code(String label, String value) {
        details.add(new Detail(label, value, true));
        return this;
    }
}
