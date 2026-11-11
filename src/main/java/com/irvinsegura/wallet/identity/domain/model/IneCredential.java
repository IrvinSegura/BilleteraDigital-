package com.irvinsegura.wallet.identity.domain.model;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a Mexican voter card (INE, models E to H, the only ones still valid) says about its holder, checked for what
 * can be checked without the card itself: the CURP's format and check digit, the voter key's format, that both carry
 * the same birth date and sex, that the holder is an adult and that the card has not expired. Whether the pictures
 * show this card and this person is for the reviewer, or for a provider that queries INE's register.
 *
 * @param voterKey   the "clave de elector", 18 characters
 * @param cic        the "CIC", 9 digits on the back
 * @param citizenId  the "identificador del ciudadano", 9 digits on the back
 * @param expiryYear the year the card stops being valid ("vigencia")
 */
public record IneCredential(String firstNames, String firstSurname, String secondSurname, String curp,
        String voterKey, String cic, String citizenId, int expiryYear) {

    public static final int ADULT_AGE = 18;
    // A card is valid for ten years from the year it was issued
    private static final int MAX_VALIDITY_YEARS = 10;

    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L} .'-]{0,59}");
    private static final Pattern CURP = Pattern.compile("[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])"
            + "[HMX](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)"
            + "[B-DF-HJ-NP-TV-Z]{3}[A-Z\\d]\\d");
    private static final Pattern VOTER_KEY = Pattern.compile("[A-Z]{6}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])"
            + "(0[1-9]|[12]\\d|3[0-2])[HMX]\\d{3}");
    private static final Pattern NINE_DIGITS = Pattern.compile("\\d{9}");
    private static final String CURP_ALPHABET = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";
    private static final Set<String> SURNAME_PARTICLES = Set.of("DA", "DAS", "DE", "DEL", "DER", "DI", "DIE", "DD",
            "EL", "LA", "LOS", "LAS", "LE", "LES", "MAC", "MC", "VAN", "VON", "Y");
    // The CURP skips them when another name follows: "José Luis" is coded by Luis
    private static final Set<String> COMMON_FIRST_NAMES = Set.of("JOSE", "J", "MARIA", "MA", "M");

    /**
     * Normalizes what was typed (spaces, case) and checks it.
     *
     * @throws InvalidIdentityDocumentException with the first problem found
     */
    public static IneCredential of(String firstNames, String firstSurname, String secondSurname, String curp,
            String voterKey, String cic, String citizenId, int expiryYear, LocalDate today) {
        IneCredential credential = new IneCredential(name(firstNames), name(firstSurname),
                secondSurname == null || secondSurname.isBlank() ? null : name(secondSurname), code(curp),
                code(voterKey), digits(cic), digits(citizenId), expiryYear);
        credential.check(today);
        return credential;
    }

    private void check(LocalDate today) {
        if (!NAME.matcher(firstNames).matches() || !NAME.matcher(firstSurname).matches()
                || secondSurname != null && !NAME.matcher(secondSurname).matches()) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.NAME_INVALID);
        }
        if (!CURP.matcher(curp).matches() || !curpCheckDigitMatches(curp) || curpBirthDate(curp) == null) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.CURP_INVALID);
        }
        if (!VOTER_KEY.matcher(voterKey).matches()) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.VOTER_KEY_INVALID);
        }
        // Both codes come from the same birth record: same date (yyMMdd) and same sex
        if (!voterKey.substring(6, 12).equals(curp.substring(4, 10)) || voterKey.charAt(14) != curp.charAt(10)) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.DATA_MISMATCH);
        }
        if (!NINE_DIGITS.matcher(cic).matches()) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.CIC_INVALID);
        }
        if (!NINE_DIGITS.matcher(citizenId).matches()) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.CITIZEN_ID_INVALID);
        }
        if (expiryYear < today.getYear() || expiryYear > today.getYear() + MAX_VALIDITY_YEARS) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.EXPIRED);
        }
        if (birthDate().plusYears(ADULT_AGE).isAfter(today)) {
            throw new InvalidIdentityDocumentException(InvalidIdentityDocumentException.UNDERAGE);
        }
    }

    public LocalDate birthDate() {
        return curpBirthDate(curp);
    }

    /**
     * {@code H}, {@code M} or {@code X}, as the CURP has it.
     */
    public char sex() {
        return curp.charAt(10);
    }

    public String fullName() {
        return secondSurname == null ? firstNames + " " + firstSurname
                : firstNames + " " + firstSurname + " " + secondSurname;
    }

    /**
     * Whether the CURP's first four letters are the ones these names give. A hint for the reviewer, not a rule: the
     * CURP has exceptions (words it avoids, some foreign names) this does not know.
     */
    public boolean namesMatchCurp() {
        String surname = meaningful(words(firstSurname));
        List<String> given = words(firstNames);
        if (surname.isEmpty() || given.isEmpty()) {
            return false;
        }
        char vowel = 'X';
        for (int i = 1; i < surname.length(); i++) {
            if ("AEIOU".indexOf(surname.charAt(i)) >= 0) {
                vowel = surname.charAt(i);
                break;
            }
        }
        String other = secondSurname == null ? "" : meaningful(words(secondSurname));
        char second = other.isEmpty() ? 'X' : other.charAt(0);
        String name = given.size() > 1 && COMMON_FIRST_NAMES.contains(given.getFirst()) ? given.get(1) : given.getFirst();
        return curp.charAt(0) == surname.charAt(0) && (curp.charAt(1) == vowel || curp.charAt(1) == 'X')
                && curp.charAt(2) == second && curp.charAt(3) == name.charAt(0);
    }

    /**
     * Whether every word of the account's name is on the card, ignoring accents and case, so "Ana Pérez" matches
     * "ANA SOFÍA PÉREZ LÓPEZ" but "Ana Gómez" does not.
     */
    public boolean includesName(String accountName) {
        List<String> card = words(fullName());
        List<String> account = words(accountName);
        return !account.isEmpty() && card.containsAll(account);
    }

    /**
     * The CURP's last four characters, enough to tell cards apart on screen without showing the whole code.
     */
    public String curpLastFour() {
        return curp.substring(curp.length() - 4);
    }

    static boolean curpCheckDigitMatches(String curp) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int value = CURP_ALPHABET.indexOf(curp.charAt(i));
            if (value < 0) {
                return false;
            }
            sum += value * (18 - i);
        }
        return curp.charAt(17) - '0' == (10 - sum % 10) % 10;
    }

    // Born in the 1900s when the 17th character is a digit, in the 2000s when it is a letter
    private static LocalDate curpBirthDate(String curp) {
        int century = Character.isDigit(curp.charAt(16)) ? 1900 : 2000;
        try {
            return LocalDate.of(century + Integer.parseInt(curp.substring(4, 6)), Integer.parseInt(curp.substring(6, 8)),
                    Integer.parseInt(curp.substring(8, 10)));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static String meaningful(List<String> words) {
        return words.stream().filter(word -> !SURNAME_PARTICLES.contains(word)).findFirst()
                .orElse(words.isEmpty() ? "" : words.getFirst());
    }

    // Upper case without accents; Ñ is written X in the CURP
    static List<String> words(String text) {
        String upper = text.toUpperCase(Locale.ROOT).replace('Ñ', 'X');
        String plain = Normalizer.normalize(upper, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(plain.split("[^A-Z]+")).filter(word -> !word.isEmpty()).toList();
    }

    private static String name(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String code(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String digits(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
