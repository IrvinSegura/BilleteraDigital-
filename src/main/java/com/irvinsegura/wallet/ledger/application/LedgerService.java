package com.irvinsegura.wallet.ledger.application;

import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.ledger.Ledger;
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
import java.util.Currency;
import java.util.List;
import java.util.Map;
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
