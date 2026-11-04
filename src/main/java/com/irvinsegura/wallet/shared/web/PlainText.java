package com.irvinsegura.wallet.shared.web;

/**
 * Free text one user types and another one reads (names, transfer concepts): it ends up in notifications, emails
 * and other users' screens, so markup and control characters are refused instead of trusting every reader to
 * escape them.
 */
public final class PlainText {

    /** For {@code @Pattern}: no {@code <}, {@code >} or control characters. */
    public static final String NO_MARKUP = "[^<>\\p{Cntrl}]*";

    public static final String NO_MARKUP_MESSAGE = "must not contain <, > or control characters";

    private PlainText() {
    }
}
