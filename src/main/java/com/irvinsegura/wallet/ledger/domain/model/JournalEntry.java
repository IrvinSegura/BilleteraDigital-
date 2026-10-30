package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable record of money moving between accounts. Debits always equal credits, so money is never created or
 * destroyed; corrections are new entries, never edits.
 */
public final class JournalEntry {

    private static final Pattern TYPE_FORMAT = Pattern.compile("[A-Z][A-Z_]{0,29}");
    private static final int MAX_REFERENCE_LENGTH = 100;

    private final UUID id;
    private final String type;
    private final String reference;
    private final Instant postedAt;
    private final List<Posting> postings;

    private JournalEntry(UUID id, String type, String reference, Instant postedAt, List<Posting> postings) {
        this.id = id;
        this.type = type;
        this.reference = reference;
        this.postedAt = postedAt;
        this.postings = List.copyOf(postings);
    }

    /**
     * Validates the lines and applies them to the given accounts, which must already be locked by the caller.
     * Nothing is stored if a line fails, because the caller's transaction rolls back.
     */
    public static JournalEntry post(String type, String reference, List<EntryLine> lines,
            Map<UUID, LedgerAccount> accounts, Instant now) {
        if (type == null || !TYPE_FORMAT.matcher(type).matches()) {
            throw new IllegalArgumentException("type must be 1 to 30 uppercase letters or underscores: " + type);
        }
        if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
            throw new IllegalArgumentException("reference must have at most %d characters"
                    .formatted(MAX_REFERENCE_LENGTH));
        }
        requireBalanced(lines);

        List<Posting> postings = new ArrayList<>(lines.size());
        for (EntryLine line : lines) {
            LedgerAccount account = accounts.get(line.accountId());
            if (account == null) {
                throw new IllegalArgumentException("Unknown ledger account " + line.accountId());
            }
            Money balanceAfter = account.apply(line.side(), line.amount());
            postings.add(new Posting(UUID.randomUUID(), account.id(), line.side(), line.amount(), balanceAfter));
        }
        return new JournalEntry(UUID.randomUUID(), type, reference, now, postings);
    }

    private static void requireBalanced(List<EntryLine> lines) {
        Objects.requireNonNull(lines, "lines must not be null");
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least two lines");
        }
        if (new HashSet<>(lines.stream().map(EntryLine::accountId).toList()).size() != lines.size()) {
            throw new IllegalArgumentException("Each account may appear only once per entry");
        }
        Money debits = Money.zero(lines.getFirst().amount().currency());
        Money credits = debits;
        for (EntryLine line : lines) {
            if (line.amount().isZero()) {
                throw new InvalidAmountException("Amount must be greater than zero");
            }
            if (line.side() == Side.DEBIT) {
                debits = debits.plus(line.amount());
            } else {
                credits = credits.plus(line.amount());
            }
        }
        if (!debits.equals(credits)) {
            throw new IllegalArgumentException("Unbalanced entry: debits %s, credits %s".formatted(debits, credits));
        }
    }

    public UUID id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String reference() {
        return reference;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public List<Posting> postings() {
        return postings;
    }
}
