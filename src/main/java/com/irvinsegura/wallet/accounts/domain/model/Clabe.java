package com.irvinsegura.wallet.accounts.domain.model;

/**
 * A Mexican interbank account number (CLABE), the destination of every SPEI payment: 3 digits of bank, 3 of
 * city, 11 of account and a check digit. The check digit catches most typos before money is sent to the wrong place.
 */
public record Clabe(String value) {

    private static final int LENGTH = 18;
    // Weights Banxico assigns to the first 17 digits, repeating
    private static final int[] WEIGHTS = {3, 7, 1};

    public Clabe {
        if (value == null || !value.matches("\\d{" + LENGTH + "}")) {
            throw new InvalidClabeException("A CLABE has exactly 18 digits");
        }
        if (checkDigit(value) != value.charAt(LENGTH - 1) - '0') {
            throw new InvalidClabeException("The CLABE's check digit does not match; check for a typo");
        }
    }

    /**
     * Accepts the number as people write it, with spaces or dashes between groups.
     */
    public static Clabe parse(String raw) {
        return new Clabe(raw == null ? null : raw.replaceAll("[\\s-]", ""));
    }

    static int checkDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i % WEIGHTS.length] % 10;
        }
        return (10 - sum % 10) % 10;
    }

    public String bankCode() {
        return value.substring(0, 3);
    }

    public String lastFour() {
        return value.substring(LENGTH - 4);
    }

    // Keeps the full number out of logs; only the provider that pays into it needs it
    @Override
    public String toString() {
        return "CLABE ending " + lastFour();
    }
}
