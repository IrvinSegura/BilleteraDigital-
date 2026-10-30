package com.irvinsegura.wallet.ledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.irvinsegura.wallet.ledger.InsufficientFundsException;
import com.irvinsegura.wallet.shared.domain.CurrencyMismatchException;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final Currency MXN = Currency.getInstance("MXN");

    private final LedgerAccount settlement = LedgerAccount.restore(UUID.randomUUID(), AccountType.ASSET,
            Money.ofMinor(0, "MXN"), LedgerAccount.SETTLEMENT, NOW, 0L);
    private final LedgerAccount alice = LedgerAccount.openUserAccount(MXN, NOW);
    private final LedgerAccount bob = LedgerAccount.openUserAccount(MXN, NOW);
    private final Map<UUID, LedgerAccount> accounts =
            Map.of(settlement.id(), settlement, alice.id(), alice, bob.id(), bob);

    @Test
    void depositIncreasesBothTheAssetAndTheLiability() {
        JournalEntry entry = post("DEPOSIT", settlement, alice, 10_000);

        assertThat(settlement.balance()).isEqualTo(mxn(10_000));
        assertThat(alice.balance()).isEqualTo(mxn(10_000));
        assertThat(entry.postings())
                .extracting(Posting::side, Posting::balanceAfter)
                .containsExactly(
                        tuple(Side.DEBIT, mxn(10_000)),
                        tuple(Side.CREDIT, mxn(10_000)));
    }

    @Test
    void transferBetweenUsersMovesMoneyWithoutCreatingAny() {
        post("DEPOSIT", settlement, alice, 10_000);

        post("TRANSFER", alice, bob, 2_550);

        assertThat(alice.balance()).isEqualTo(mxn(7_450));
        assertThat(bob.balance()).isEqualTo(mxn(2_550));
        assertThat(settlement.balance()).isEqualTo(alice.balance().plus(bob.balance()));
    }

    @Test
    void rejectsEntriesThatWouldLeaveANegativeBalance() {
        post("DEPOSIT", settlement, alice, 1_000);

        assertThatThrownBy(() -> post("TRANSFER", alice, bob, 1_001))
                .isInstanceOf(InsufficientFundsException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void rejectsUnbalancedEntries() {
        List<EntryLine> lines = List.of(
                new EntryLine(settlement.id(), Side.DEBIT, mxn(100)),
                new EntryLine(alice.id(), Side.CREDIT, mxn(99)));

        assertThatThrownBy(() -> JournalEntry.post("DEPOSIT", null, lines, accounts, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unbalanced");
    }

    @Test
    void rejectsTheSameAccountOnBothSides() {
        assertThatThrownBy(() -> post("TRANSFER", alice, alice, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAmounts() {
        assertThatThrownBy(() -> post("DEPOSIT", settlement, alice, 0))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejectsAmountsInAnotherCurrency() {
        List<EntryLine> lines = List.of(
                new EntryLine(settlement.id(), Side.DEBIT, Money.ofMinor(100, "USD")),
                new EntryLine(alice.id(), Side.CREDIT, Money.ofMinor(100, "USD")));

        assertThatThrownBy(() -> JournalEntry.post("DEPOSIT", null, lines, accounts, NOW))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejectsMalformedTypes() {
        assertThatThrownBy(() -> post("deposit", settlement, alice, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JournalEntry post(String type, LedgerAccount debit, LedgerAccount credit, long minorUnits) {
        List<EntryLine> lines = List.of(
                new EntryLine(debit.id(), Side.DEBIT, mxn(minorUnits)),
                new EntryLine(credit.id(), Side.CREDIT, mxn(minorUnits)));
        return JournalEntry.post(type, null, lines, accounts, NOW);
    }

    private static Money mxn(long minorUnits) {
        return Money.ofMinor(minorUnits, "MXN");
    }
}
