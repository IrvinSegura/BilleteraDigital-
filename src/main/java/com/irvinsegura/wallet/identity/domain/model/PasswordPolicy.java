package com.irvinsegura.wallet.identity.domain.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Password rules following NIST SP 800-63B: length instead of composition rules, long passphrases allowed, and
 * passwords that are easy to guess refused. Those are the common ones, trivial patterns (repeats, sequences,
 * keyboard walks), a common word dressed up with digits and symbols ({@code P@ssw0rd2024!}) and the user's own name
 * or email ({@code AnaLopez1990}).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    // BCrypt ignores everything after 72 bytes, so longer passwords would give a false sense of strength
    private static final int MAX_BYTES = 72;
    // Fewer different characters than this is a pattern however long it is: "aaaaaaaaaaaa", "121212121212"
    private static final int MIN_DISTINCT = 5;
    // Shorter name or email parts ("ana", "li") are too likely to appear by chance in a good passphrase
    private static final int MIN_PERSONAL_LENGTH = 4;
    // Below this, what is left after dropping symbols is too short to call a sequence
    private static final int MIN_WALK_LENGTH = 6;

    private static final Set<String> COMMON = load("common-passwords.txt");

    // Doubled, so a walk may wrap around ("7890123456"), and reversed, so it may go either way
    private static final List<String> WALKS = Stream.of("0123456789", "abcdefghijklmnopqrstuvwxyz",
                    "qwertyuiopasdfghjklzxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik9ol0p", "1q2w3e4r5t6y7u8i9o0p",
                    "qazwsxedcrfvtgbyhnujmikolp", "zaq1xsw2cde3vfr4bgt5nhy6mju7ki8lo9p0")
            .map(walk -> walk + walk)
            .flatMap(walk -> Stream.of(walk, new StringBuilder(walk).reverse().toString()))
            .toList();

    private static final Map<Character, Character> LEET = Map.of('@', 'a', '4', 'a', '3', 'e', '1', 'i', '!', 'i',
            '0', 'o', '$', 's', '5', 's', '7', 't');

    private PasswordPolicy() {
    }

    /**
     * @param personal what the password must not be built from: the user's email and name
     * @throws WeakPasswordException   if it is blank, too short or too long
     * @throws CommonPasswordException if it is easy to guess
     */
    public static void check(String rawPassword, String... personal) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new WeakPasswordException("Password must not be blank");
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new WeakPasswordException(
                    "Password must have between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new WeakPasswordException("Password must not exceed %d bytes".formatted(MAX_BYTES));
        }
        if (isEasyToGuess(normalize(rawPassword), personalWords(personal))) {
            throw new CommonPasswordException();
        }
    }

    private static boolean isEasyToGuess(String password, Set<String> personal) {
        if (password.codePoints().distinct().count() < MIN_DISTINCT) {
            return true;
        }
        // Spaces and symbols don't make "1234 5678 9012" or "qwerty-uiop-as" any harder to guess
        String compact = password.replaceAll("[^\\p{L}\\p{N}]", "");
        if (COMMON.contains(compact) || isRepeated(compact) || isWalk(compact)) {
            return true;
        }
        String unit = unitOf(core(password));
        return COMMON.contains(unit) || personal.contains(unit);
    }

    // The word inside the digits and symbols people add to meet composition rules, with look-alike characters
    // read back as letters: "P@ssw0rd2024!" is "password"
    private static String core(String password) {
        int start = 0;
        int end = password.length();
        while (start < end && !isLetterLike(password.charAt(start))) {
            start++;
        }
        while (end > start && !isLetterLike(password.charAt(end - 1))) {
            end--;
        }
        StringBuilder core = new StringBuilder();
        for (char c : password.substring(start, end).toCharArray()) {
            char letter = LEET.getOrDefault(c, c);
            if (Character.isLetter(letter)) {
                core.append(letter);
            }
        }
        return core.toString();
    }

    // "@dmin" starts with a letter in disguise; other symbols at the ends are the usual "2024!" decoration
    private static boolean isLetterLike(char c) {
        return Character.isLetter(c) || c == '@';
    }

    private static boolean isRepeated(String text) {
        return !unitOf(text).equals(text);
    }

    // The shortest piece the text repeats: "abcabcabc" gives "abc"; text that isn't a repeat is its own unit
    private static String unitOf(String text) {
        return text.isEmpty() ? text : text.substring(0, (text + text).indexOf(text, 1));
    }

    private static boolean isWalk(String text) {
        return text.length() >= MIN_WALK_LENGTH && WALKS.stream().anyMatch(walk -> walk.contains(text));
    }

    // "Ana López" gives "analopez" and "lopez"; "ana.lopez90@mail.com" gives "analopez" too
    private static Set<String> personalWords(String... personal) {
        Set<String> words = new HashSet<>();
        Arrays.stream(personal).filter(Objects::nonNull).map(PasswordPolicy::normalize).forEach(value -> {
            String text = value.contains("@") ? value.substring(0, value.indexOf('@')) : value;
            words.add(text.replaceAll("\\P{L}", ""));
            words.addAll(Arrays.asList(text.split("\\P{L}+")));
        });
        words.removeIf(word -> word.length() < MIN_PERSONAL_LENGTH);
        return words;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // One entry per line, '#' starts a comment; compared after the same normalization as the password
    private static Set<String> load(String resource) {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing password list " + resource);
            }
            Set<String> entries = new HashSet<>();
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(PasswordPolicy::normalize)
                    .forEach(entries::add);
            return Set.copyOf(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
