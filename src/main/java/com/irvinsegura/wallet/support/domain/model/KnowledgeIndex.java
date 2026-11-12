package com.irvinsegura.wallet.support.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the section that answers a question, by the words they share. No AI and nothing leaves the app: it compares
 * words, ignoring case, accents, the words every question has, endings ("retiro" finds "retirar") and typing mistakes
 * ("depsoito" finds "deposito"), after turning words that mean the same into one ("plata" into "dinero").
 *
 * <p>Not every word counts the same. A word few sections talk about says more than one most do, a word found exactly
 * more than one that only resembles it, and a section with the whole phrase written, in any of its endings, more than
 * one with its words scattered. A word that is already right is never taken for another one mistyped: "saldo" is not "salgo".
 *
 * <p>A wrong answer said with confidence is worse than none, so it only answers when one section clearly wins, or when
 * it is the only one close to what was asked. When several are close it offers them instead, and the user picks.
 *
 * <p>Support is for problems, and questions about the app are for the bot. So when it finds no answer it depends on
 * what was written. Someone reporting a problem goes to a person. What can't be read ("k", "asdf", a single unknown word)
 * is asked again, since a person couldn't do more with it. When only part of it matches, the bot offers what it may have
 * meant. A sentence it can read but knows nothing about is asked once more another way, and {@link BotReply#unanswered}
 * tells the caller to pass it on if that happens twice. A word that can't be read among others that can is ignored, so
 * "como retiro a mi ksjdh" is still answered.
 */
public final class KnowledgeIndex {

    // Below this it isn't sure enough of a section to answer with it
    private static final double MIN_SCORE = 0.6;
    // Enough when no other section comes close: offering a single option would only add a click
    private static final double MIN_LONE_SCORE = 0.45;
    // How far ahead of the next section the best one has to be, or the user gets to choose between them
    private static final double MIN_LEAD = 0.05;
    // Below this a section is not worth offering, and below this share of the best one it would only distract
    private static final double MIN_NEARBY_SCORE = 0.4;
    private static final double MIN_NEARBY_SHARE_OF_BEST = 0.5;

    // Where a word was found: alone as a keyword it is what the section is about, in its text only in passing
    private static final double AS_KEYWORD = 1;
    private static final double IN_TITLE = 0.8;
    private static final double IN_KEYWORD_PHRASE = 0.6;
    private static final double IN_BODY = 0.25;
    // How it was found
    private static final double EXACTLY = 1;
    private static final double WITH_ANOTHER_ENDING = 0.85;
    private static final double MISTYPED = 0.7;
    // A section that lacks the most telling word of the question is rarely about it
    private static final double WITHOUT_MAIN_WORD = 0.6;
    // A word no section is about may change what is being asked, as in "mi saldo no coincide", so the bot is less sure
    private static final double OFF_TOPIC_IMPORTANCE = 2;
    // A word only one section mentions may be there by chance, so it can't decide everything by itself
    private static final double MAX_IMPORTANCE = 3.5;
    // Phrases are written by hand for what people ask, so finding one whole says a lot
    private static final double BONUS_PER_PHRASE_WORD = 0.2;
    private static final int MAX_PHRASE_WORDS_COUNTED = 4;
    private static final int MIN_PHRASE_WORDS = 2;

    private static final int MIN_WORD_LENGTH = 2;
    private static final int SHORT_WORD_LENGTH = 4;
    private static final int SHORT_WORD_SHARED = 3;
    private static final int SHARED_AT_LEAST = 4;
    private static final int ENDING_LENGTH = 2;
    private static final int MIN_TYPO_LENGTH = 5;
    private static final int TWO_TYPOS_LENGTH = 8;

    private static final int MAX_SUGGESTIONS = 3;
    // One word can't be told from noise; two or more are a question the bot doesn't know about
    private static final int MIN_WORDS_OF_A_QUESTION = 2;

    private static final String REPLY_UNCLEAR = "unclear";
    private static final String REPLY_SUGGESTIONS = "suggestions";
    private static final String REPLY_NOT_FOUND = "not-found";
    private static final String REPLY_SYNONYMS = "synonyms";

    private static final Pattern NO_VOWELS = Pattern.compile("[^aeiouy]+");
    private static final Pattern CONSONANT_RUN = Pattern.compile(".*[^aeiouy]{5}.*");
    // "jajaja", "kkkk", "asdasd"
    private static final Pattern REPEATED = Pattern.compile("(.{1,3})\\1+");
    private static final Pattern KEYBOARD_MIDDLE_ROW = Pattern.compile("[asdfghjkl]{4,}");
    private static final String KEYBOARD_TOP_ROW = "qwertyuiop";
    private static final int MIN_WALK_LENGTH = 4;

    private static final Set<String> COMMON_WORDS = Set.of(
            "a", "al", "algo", "alguien", "ante", "aqui", "asi", "como", "con", "cual", "cuales", "cuando", "cuanto",
            "cuanta", "cuantos", "cuantas", "de", "del", "desde", "donde", "el", "ella", "ellos", "en", "entre", "es",
            "esa", "ese", "eso", "esta", "este", "esto", "estoy", "ha", "hace", "hacer", "hago", "hay", "la", "las",
            "le", "les", "lo", "los", "me", "mi", "mis", "mucho", "muy", "necesito", "ni", "no", "nos", "o", "para",
            "pero", "por", "porque", "puede", "pueden", "puedo", "que", "quien", "quiero", "se", "ser", "si", "sin",
            "sobre", "son", "su", "sus", "tan", "te", "tengo", "ti", "tiene", "tu", "tus", "un", "una", "uno", "unos",
            "unas", "usted", "y", "ya", "yo", "favor", "ayuda", "ayudar", "ayudame", "tener", "tienen", "acepta", "aceptan",
            "maneja", "manejan", "ofrece", "ofrecen", "soporta", "soportan", "sistema", "plataforma", "usar", "usa", "uso",
            "saber", "quisiera", "gustaria", "podria", "podrias", "dime", "dame", "explica", "explicame", "informacion",
            "info", "duda", "dudas", "pregunta", "preguntas", "tema", "temas", "basico", "algun", "alguna", "decir",
            "digan", "cuentame", "cuentanos", "kiero", "hoy", "he", "hemos", "dan", "dar", "dieron", "aca", "alla",
            "about", "after", "am", "an", "and", "any", "are", "at", "be", "but", "by", "can", "could", "did", "do",
            "does", "for", "from", "get", "had", "has", "have", "help", "how", "i", "if", "in", "into", "is", "it",
            "its", "just", "many", "much", "my", "need", "not", "of", "on", "or", "please", "should", "so", "some",
            "that", "the", "their", "them", "then", "there", "this", "to", "too", "us", "want", "was", "we", "were",
            "what", "when", "where", "which", "who", "why", "will", "with", "would", "you", "your", "accept", "accepts",
            "offer", "offers", "handle", "handles", "system", "platform", "use", "uses", "tell", "explain", "know",
            "information", "question", "questions", "basic", "topic", "here");

    private final Map<String, String> synonyms = new HashMap<>();
    private final List<Entry> entries;
    private final Set<String> vocabulary = new HashSet<>();
    private final String unclear;
    private final String suggestions;
    private final String notFound;
    private final Set<String> problemWords = new HashSet<>();

    /**
     * @throws IllegalStateException if it lacks what to say when it finds no answer: better to find out when the app starts
     */
    public KnowledgeIndex(List<KnowledgeSection> sections) {
        sections.stream()
                .filter(section -> REPLY_SYNONYMS.equals(section.reply()))
                .forEach(section -> section.body().lines().forEach(this::addSynonyms));
        this.entries = sections.stream().filter(section -> section.reply() == null).map(this::entryOf).toList();
        entries.forEach(entry -> {
            vocabulary.addAll(entry.keywords());
            vocabulary.addAll(entry.phraseWords());
            vocabulary.addAll(entry.title());
            vocabulary.addAll(entry.body());
        });
        this.unclear = replyFor(sections, REPLY_UNCLEAR);
        this.suggestions = replyFor(sections, REPLY_SUGGESTIONS);
        this.notFound = replyFor(sections, REPLY_NOT_FOUND);
        sections.stream()
                .filter(section -> REPLY_NOT_FOUND.equals(section.reply()))
                .flatMap(section -> section.keywords().stream())
                .forEach(keyword -> problemWords.addAll(words(fold(keyword))));
    }

    // "dinero: plata, lana": the word the sections use, and the ones people may use for it
    private void addSynonyms(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return;
        }
        String word = fold(line.substring(0, colon)).strip();
        Arrays.stream(line.substring(colon + 1).split(","))
                .map(other -> fold(other).strip())
                .filter(other -> !other.isEmpty())
                .forEach(other -> synonyms.put(other, word));
    }

    private Entry entryOf(KnowledgeSection section) {
        Set<String> keywords = new HashSet<>();
        Set<String> phraseWords = new HashSet<>();
        List<Phrase> phrases = new ArrayList<>();
        for (String keyword : section.keywords()) {
            List<String> tokens = tokens(fold(keyword));
            Set<String> words = words(fold(keyword));
            if (tokens.size() < MIN_PHRASE_WORDS) {
                keywords.addAll(words);
            } else {
                phraseWords.addAll(words);
                // "que hace" says nothing about what is being asked
                if (!words.isEmpty()) {
                    phrases.add(new Phrase(tokens));
                }
            }
        }
        return new Entry(section, keywords, phraseWords, words(fold(section.title())), words(fold(section.body())),
                phrases);
    }

    public Optional<KnowledgeSection> find(String question) {
        return search(question).answer();
    }

    public BotReply reply(String question) {
        Search search = search(question);
        Optional<KnowledgeSection> answer = search.answer();
        Set<String> words = readable(fold(question));
        // Explaining how something works doesn't solve a problem, unless the answer already hands it to a person
        if (words.stream().anyMatch(word -> quality(word, problemWords) > 0)) {
            return answer.filter(KnowledgeSection::escalate).map(BotReply::answering)
                    .orElseGet(BotReply::passingToAPerson);
        }
        if (answer.isPresent()) {
            return BotReply.answering(answer.get());
        }
        if (words.isEmpty()) {
            return BotReply.askingAgain(unclear);
        }
        if (!search.nearby().isEmpty()) {
            return BotReply.suggesting(suggestions, search.nearby());
        }
        return words.size() < MIN_WORDS_OF_A_QUESTION ? BotReply.askingAgain(unclear) : BotReply.notKnowing(notFound);
    }

    private Search search(String question) {
        String folded = fold(question);
        Set<String> words = readable(folded);
        if (words.isEmpty()) {
            return new Search(Optional.empty(), List.of());
        }
        // A suggestion the bot offered comes back as its title, which must lead to that section and no other
        String plain = plain(folded);
        Optional<KnowledgeSection> byTitle = entries.stream()
                .filter(entry -> plain(fold(entry.section().title())).equals(plain))
                .map(Entry::section)
                .findFirst();
        if (byTitle.isPresent()) {
            return new Search(byTitle, List.of());
        }
        Map<String, Double> importance = importanceOf(words);
        List<String> sentence = tokens(folded);
        List<Match> ranked = entries.stream()
                .map(entry -> new Match(entry.section(), score(entry, importance, sentence)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .toList();
        if (ranked.isEmpty()) {
            return new Search(Optional.empty(), List.of());
        }
        Match best = ranked.getFirst();
        boolean ahead = ranked.size() == 1 || best.score() - ranked.get(1).score() >= MIN_LEAD;
        if (best.score() >= MIN_SCORE && ahead) {
            return new Search(Optional.of(best.section()), List.of());
        }
        List<String> nearby = nearby(ranked, best.score());
        boolean alone = nearby.size() == 1 && nearby.getFirst().equals(best.section().title());
        return alone && best.score() >= MIN_LONE_SCORE
                ? new Search(Optional.of(best.section()), List.of())
                : new Search(Optional.empty(), nearby);
    }

    // The sections close enough to what was asked, the closest first
    private static List<String> nearby(List<Match> ranked, double best) {
        double enough = Math.max(MIN_NEARBY_SCORE, best * MIN_NEARBY_SHARE_OF_BEST);
        return ranked.stream()
                .filter(match -> !match.section().smallTalk() && match.score() >= enough)
                .limit(MAX_SUGGESTIONS)
                .map(match -> match.section().title())
                .toList();
    }

    // How much each word of the question tells: the fewer sections are about it, the more
    private Map<String, Double> importanceOf(Set<String> words) {
        Map<String, Double> importance = new HashMap<>();
        for (String word : words) {
            long about = entries.stream().filter(entry -> topicWeight(entry, word) > 0).count();
            importance.put(word, about == 0 ? OFF_TOPIC_IMPORTANCE
                    : Math.clamp(Math.log(1 + (double) entries.size() / about), OFF_TOPIC_IMPORTANCE, MAX_IMPORTANCE));
        }
        return importance;
    }

    // From 0 to a little over 1: how much of what the question says this section is about
    private double score(Entry entry, Map<String, Double> importance, List<String> sentence) {
        double total = 0;
        double found = 0;
        boolean prominent = false;
        boolean everyWord = true;
        String main = null;
        for (Map.Entry<String, Double> word : importance.entrySet()) {
            double topic = topicWeight(entry, word.getKey());
            double weight = topic > 0 ? topic : IN_BODY * quality(word.getKey(), entry.body());
            total += word.getValue();
            found += word.getValue() * weight;
            prominent |= topic > 0;
            everyWord &= weight > 0;
            if (main == null || word.getValue() > importance.get(main)) {
                main = word.getKey();
            }
        }
        int phraseWords = entry.phrases().stream()
                .filter(phrase -> phrase.foundIn(sentence))
                .mapToInt(phrase -> phrase.tokens().size())
                .max()
                .orElse(0);
        // A greeting among other words is not what was asked
        if ((!prominent && phraseWords == 0) || (entry.section().smallTalk() && !everyWord)) {
            return 0;
        }
        double covered = found / total;
        if (importance.get(main) > OFF_TOPIC_IMPORTANCE && topicWeight(entry, main) == 0
                && quality(main, entry.body()) == 0) {
            covered *= WITHOUT_MAIN_WORD;
        }
        return covered + Math.min(phraseWords, MAX_PHRASE_WORDS_COUNTED) * BONUS_PER_PHRASE_WORD;
    }

    // How much a section is about a word, leaving out what its text only mentions
    private double topicWeight(Entry entry, String word) {
        return Math.max(AS_KEYWORD * quality(word, entry.keywords()), Math.max(
                IN_TITLE * quality(word, entry.title()), IN_KEYWORD_PHRASE * quality(word, entry.phraseWords())));
    }

    private static String replyFor(List<KnowledgeSection> sections, String reply) {
        return sections.stream()
                .filter(section -> reply.equals(section.reply()))
                .map(KnowledgeSection::body)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The support bot has no 'reply: " + reply + "' section"));
    }

    private static String plain(String folded) {
        return folded.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    // The words of a question, without what can't be read
    private Set<String> readable(String folded) {
        Set<String> words = words(folded);
        words.removeIf(word -> looksLikeNoise(word) && quality(word, vocabulary) == 0);
        return words;
    }

    // Typed without meaning anything. A word the bot knows is never noise, whatever it looks like ("mxn", "qr", "2fa")
    private static boolean looksLikeNoise(String word) {
        return NO_VOWELS.matcher(word).matches()
                || CONSONANT_RUN.matcher(word).matches()
                || REPEATED.matcher(word).matches()
                || KEYBOARD_MIDDLE_ROW.matcher(word).matches()
                || isKeyboardWalk(word);
    }

    private static boolean isKeyboardWalk(String word) {
        return word.length() >= MIN_WALK_LENGTH && (KEYBOARD_TOP_ROW.contains(word)
                || new StringBuilder(KEYBOARD_TOP_ROW).reverse().toString().contains(word));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private List<String> tokens(String folded) {
        return Arrays.stream(folded.split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> synonyms.getOrDefault(word, word))
                .toList();
    }

    private Set<String> words(String folded) {
        Set<String> words = new HashSet<>(tokens(folded));
        words.removeIf(word -> word.length() < MIN_WORD_LENGTH || COMMON_WORDS.contains(word));
        return words;
    }

    // How well a word is found among others, from 0 for not at all. A word the bot knows as it is is no mistake
    private double quality(String word, Set<String> words) {
        if (words.contains(word)) {
            return EXACTLY;
        }
        if (words.stream().anyMatch(other -> sameStem(word, other))) {
            return WITH_ANOTHER_ENDING;
        }
        return !vocabulary.contains(word) && words.stream().anyMatch(other -> mistyped(word, other)) ? MISTYPED : 0;
    }

    // The same word with another ending: "retiro" and "retirar", "pago" and "pagar"
    private static boolean sameStem(String one, String other) {
        int shorter = Math.min(one.length(), other.length());
        // Two letters are no ending of anything: "re", from "you're", is not "refund"
        if (shorter < SHORT_WORD_SHARED) {
            return false;
        }
        int shared = 0;
        while (shared < shorter && one.charAt(shared) == other.charAt(shared)) {
            shared++;
        }
        int needed = shorter <= SHORT_WORD_LENGTH ? SHORT_WORD_SHARED : Math.max(SHARED_AT_LEAST, shorter - ENDING_LENGTH);
        return shared >= Math.min(needed, shorter);
    }

    // Short words are left out, because one letter makes them another word: "mes" and "más"
    private static boolean mistyped(String one, String other) {
        int shorter = Math.min(one.length(), other.length());
        if (shorter < MIN_TYPO_LENGTH || one.charAt(0) != other.charAt(0)) {
            return false;
        }
        int allowed = shorter < TWO_TYPOS_LENGTH ? 1 : 2;
        return Math.abs(one.length() - other.length()) <= allowed && typos(one, other) <= allowed;
    }

    // Edit distance, where swapping two neighboring letters counts as one mistake
    private static int typos(String one, String other) {
        int[][] distance = new int[one.length() + 1][other.length() + 1];
        for (int i = 0; i <= one.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= other.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= one.length(); i++) {
            for (int j = 1; j <= other.length(); j++) {
                int replaced = distance[i - 1][j - 1] + (one.charAt(i - 1) == other.charAt(j - 1) ? 0 : 1);
                int best = Math.min(replaced, Math.min(distance[i - 1][j], distance[i][j - 1]) + 1);
                boolean swapped = i > 1 && j > 1 && one.charAt(i - 1) == other.charAt(j - 2)
                        && one.charAt(i - 2) == other.charAt(j - 1);
                distance[i][j] = swapped ? Math.min(best, distance[i - 2][j - 2] + 1) : best;
            }
        }
        return distance[one.length()][other.length()];
    }

    private record Match(KnowledgeSection section, double score) {
    }

    // What a question led to: the section that answers it, or else the ones it may have meant
    private record Search(Optional<KnowledgeSection> answer, List<String> nearby) {
    }

    // A keyword of several words, which must appear one after another in a question
    private record Phrase(List<String> tokens) {

        boolean foundIn(List<String> sentence) {
            for (int start = 0; start + tokens.size() <= sentence.size(); start++) {
                int matched = 0;
                while (matched < tokens.size() && sameForm(tokens.get(matched), sentence.get(start + matched))) {
                    matched++;
                }
                if (matched == tokens.size()) {
                    return true;
                }
            }
            return false;
        }

        // "elimino mi cuenta" is "eliminar mi cuenta", but "no" is not "nos"
        private static boolean sameForm(String one, String other) {
            return one.equals(other) || (!COMMON_WORDS.contains(one) && !COMMON_WORDS.contains(other) && sameStem(one, other));
        }
    }

    private record Entry(KnowledgeSection section, Set<String> keywords, Set<String> phraseWords, Set<String> title,
            Set<String> body, List<Phrase> phrases) {
    }
}
