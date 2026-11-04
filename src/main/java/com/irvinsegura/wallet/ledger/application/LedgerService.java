package com.irvinsegura.wallet.ledger.application;

import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.ledger.FlowTotals;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.MovementFilter;
import com.irvinsegura.wallet.ledger.MovementPage;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.ledger.UnsupportedCurrencyException;
import com.irvinsegura.wallet.ledger.domain.model.EntryLine;
import com.irvinsegura.wallet.ledger.domain.model.JournalEntry;
import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import com.irvinsegura.wallet.ledger.domain.model.Movement;
import com.irvinsegura.wallet.ledger.domain.model.Side;
import com.irvinsegura.wallet.ledger.domain.port.JournalEntryRepository;
import com.irvinsegura.wallet.ledger.domain.port.LedgerAccountRepository;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService implements Ledger {

    private final LedgerAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final Clock clock;

    LedgerService(LedgerAccountRepository accountRepository, JournalEntryRepository journalEntryRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID openUserAccount(Currency currency) {
        // A wallet in a currency nobody can fund would be useless, so support follows the settlement accounts
        settlementAccount(currency);
        LedgerAccount account = LedgerAccount.openUserAccount(currency, clock.instant());
        accountRepository.save(account);
        return account.id();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID settlementAccount(Currency currency) {
        return systemAccount(LedgerAccount.SETTLEMENT, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID payoutsInTransitAccount(Currency currency) {
        return systemAccount(LedgerAccount.PAYOUTS_IN_TRANSIT, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID providerClearingAccount(Currency currency) {
        return systemAccount(LedgerAccount.PROVIDER_CLEARING, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID unappliedPaymentsAccount(Currency currency) {
        return systemAccount(LedgerAccount.UNAPPLIED_PAYMENTS, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID providerFeesAccount(Currency currency) {
        return systemAccount(LedgerAccount.PROVIDER_FEES, currency);
    }

    private UUID systemAccount(String systemCode, Currency currency) {
        return accountRepository.findSystemAccount(systemCode, currency)
                .map(LedgerAccount::id)
                .orElseThrow(() -> new UnsupportedCurrencyException(currency.getCurrencyCode()));
    }

    @Override
    @Transactional
    public RecordedEntry record(NewJournalEntry request) {
        List<EntryLine> lines = List.of(
                new EntryLine(request.debitAccountId(), Side.DEBIT, request.amount()),
                new EntryLine(request.creditAccountId(), Side.CREDIT, request.amount()));
        Map<UUID, LedgerAccount> accounts = accountRepository
                .findAllForUpdate(lines.stream().map(EntryLine::accountId).toList()).stream()
                .collect(Collectors.toMap(LedgerAccount::id, Function.identity()));

        JournalEntry entry = JournalEntry.post(request.type(), request.reference(), lines, accounts, clock.instant());

        accounts.values().forEach(accountRepository::save);
        journalEntryRepository.save(entry);
        return new RecordedEntry(entry.id(), entry.postedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Money balanceOf(UUID accountId) {
        return requireAccount(accountId).balance();
    }

    @Override
    @Transactional(readOnly = true)
    public Money balanceAt(UUID accountId, Instant at) {
        LedgerAccount account = requireAccount(accountId);
        return new Money(journalEntryRepository.balanceBefore(accountId, at).orElse(0L), account.currency());
    }

    @Override
    @Transactional(readOnly = true)
    public MovementPage movementsOf(UUID accountId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size >= 1");
        }
        LedgerAccount account = requireAccount(accountId);
        JournalEntryRepository.MovementSlice slice = journalEntryRepository.findMovements(accountId, page, size);
        List<AccountMovement> items = slice.items().stream()
                .map(movement -> toAccountMovement(account, movement))
                .toList();
        return new MovementPage(items, page, size, slice.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public MovementPage movementsOf(UUID accountId, MovementFilter filter, int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size >= 1");
        }
        LedgerAccount account = requireAccount(accountId);
        Side side = null;
        if (filter.direction() != null) {
            // IN is whichever side raises this account's balance
            boolean in = filter.direction() == AccountMovement.Direction.IN;
            side = account.type().increasesWith(Side.DEBIT) == in ? Side.DEBIT : Side.CREDIT;
        }
        JournalEntryRepository.MovementSlice slice = journalEntryRepository.findMovements(accountId,
                new JournalEntryRepository.MovementQuery(filter.from(), filter.to(), filter.types(), side,
                        filter.referencePrefix()),
                page, size);
        List<AccountMovement> items = slice.items().stream()
                .map(movement -> toAccountMovement(account, movement))
                .toList();
        return new MovementPage(items, page, size, slice.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountMovement> movementOf(UUID accountId, UUID journalEntryId) {
        LedgerAccount account = requireAccount(accountId);
        return journalEntryRepository.findMovement(accountId, journalEntryId)
                .map(movement -> toAccountMovement(account, movement));
    }

    @Override
    @Transactional(readOnly = true)
    public FlowTotals flowsOf(UUID accountId, Instant from, Instant to) {
        LedgerAccount account = requireAccount(accountId);
        JournalEntryRepository.SideTotals totals = journalEntryRepository.sumBySide(accountId, from, to);
        // Money in is the side that raises the balance: credits for what users are owed, debits for assets
        boolean debitsRaise = account.type().increasesWith(Side.DEBIT);
        long in = debitsRaise ? totals.debitMinor() : totals.creditMinor();
        long out = debitsRaise ? totals.creditMinor() : totals.debitMinor();
        return new FlowTotals(new Money(in, account.currency()), new Money(out, account.currency()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyBalance> dailyBalancesOf(UUID accountId, LocalDate from, LocalDate to, ZoneId zone) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        LedgerAccount account = requireAccount(accountId);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        long opening = journalEntryRepository.balanceBefore(accountId, start).orElse(0L);
        return DailyBalances.fill(from, to, opening,
                journalEntryRepository.closingBalances(accountId, start, end, zone), account.currency());
    }

    private LedgerAccount requireAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ledger account " + accountId));
    }

    private static AccountMovement toAccountMovement(LedgerAccount account, Movement movement) {
        AccountMovement.Direction direction = account.type().increasesWith(movement.side())
                ? AccountMovement.Direction.IN
                : AccountMovement.Direction.OUT;
        return new AccountMovement(movement.journalEntryId(), movement.type(), movement.reference(), direction,
                movement.amount(), movement.balanceAfter(), movement.postedAt());
    }
}
