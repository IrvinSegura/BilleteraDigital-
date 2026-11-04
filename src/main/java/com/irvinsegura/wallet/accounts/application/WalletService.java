package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.WalletNotActiveException;
import com.irvinsegura.wallet.accounts.WalletNotFoundException;
import com.irvinsegura.wallet.accounts.WalletOpened;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.accounts.domain.model.DailyLimitPolicy;
import com.irvinsegura.wallet.accounts.domain.model.MovementNotFoundException;
import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.model.WalletAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.port.OutgoingUsageRepository;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.ledger.FlowTotals;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.MovementFilter;
import com.irvinsegura.wallet.ledger.MovementPage;
import com.irvinsegura.wallet.ledger.UnsupportedCurrencyException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService implements Wallets {

    // Large pages keep a statement's round trips few without loading a whole year in one query
    private static final int STATEMENT_PAGE_SIZE = 500;

    private final WalletRepository walletRepository;
    private final OutgoingUsageRepository outgoingUsageRepository;
    private final DailyLimitPolicy dailyLimitPolicy;
    private final Ledger ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    WalletService(WalletRepository walletRepository, OutgoingUsageRepository outgoingUsageRepository,
            DailyLimitPolicy dailyLimitPolicy, Ledger ledger, ApplicationEventPublisher events, Clock clock) {
        this.walletRepository = walletRepository;
        this.outgoingUsageRepository = outgoingUsageRepository;
        this.dailyLimitPolicy = dailyLimitPolicy;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public WalletSummary openWallet(UUID ownerId, String currencyCode) {
        Currency currency = parseCurrency(currencyCode);
        if (walletRepository.existsByOwnerAndCurrency(ownerId, currency)) {
            throw new WalletAlreadyExistsException(currency);
        }
        // Same transaction: if saving the wallet fails, the ledger account is rolled back with it
        Wallet wallet = Wallet.open(ownerId, currency, ledger.openUserAccount(currency), clock.instant());
        walletRepository.save(wallet);
        events.publishEvent(new WalletOpened(wallet.id(), ownerId, currency.getCurrencyCode(), wallet.createdAt()));
        return toSummary(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletSummary> listWallets(UUID ownerId) {
        return walletRepository.findByOwner(ownerId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public WalletSummary getWallet(UUID walletId, UUID ownerId) {
        return toSummary(requireOwned(walletId, ownerId));
    }

    @Transactional(readOnly = true)
    public MovementPage movements(UUID walletId, UUID ownerId, int page, int size) {
        return ledger.movementsOf(requireOwned(walletId, ownerId).ledgerAccountId(), page, size);
    }

    /**
     * The wallet's movements that match {@code criteria}, newest first.
     */
    @Transactional(readOnly = true)
    public MovementPage movements(UUID walletId, UUID ownerId, MovementCriteria criteria, int page, int size) {
        return ledger.movementsOf(requireOwned(walletId, ownerId).ledgerAccountId(), toFilter(criteria), page, size);
    }

    /**
     * One movement of the wallet, by the id of the transaction that made it, for its receipt.
     *
     * @throws MovementNotFoundException if the transaction didn't move this wallet's money
     */
    @Transactional(readOnly = true)
    public AccountMovement movement(UUID walletId, UUID ownerId, UUID transactionId) {
        return ledger.movementOf(requireOwned(walletId, ownerId).ledgerAccountId(), transactionId)
                .orElseThrow(MovementNotFoundException::new);
    }

    /**
     * Every movement from {@code from} to {@code to} (the users' days, both included), oldest first, for a
     * statement. Callers bound the period: it is read whole.
     */
    @Transactional(readOnly = true)
    public List<AccountMovement> statement(UUID walletId, UUID ownerId, LocalDate from, LocalDate to) {
        UUID accountId = requireOwned(walletId, ownerId).ledgerAccountId();
        MovementFilter filter = toFilter(new MovementCriteria(from, to, Set.of(), null, null));
        List<AccountMovement> newestFirst = new ArrayList<>();
        MovementPage page;
        int number = 0;
        do {
            page = ledger.movementsOf(accountId, filter, number++, STATEMENT_PAGE_SIZE);
            newestFirst.addAll(page.items());
        } while (page.hasNext());
        return newestFirst.reversed();
    }

    /**
     * The calendar month it is now for the users. Months and days follow the zone that sets when the daily limit
     * resets, so a statement's "today" and the limit's agree.
     */
    public YearMonth currentMonth() {
        return YearMonth.from(clock.instant().atZone(dailyLimitPolicy.zone()));
    }

    /**
     * The zone the users' days and months follow.
     */
    public ZoneId usersZone() {
        return dailyLimitPolicy.zone();
    }

    /**
     * The wallet's balances at the start and end of {@code month}, and the money in and out during it, from all of
     * its movements.
     */
    @Transactional(readOnly = true)
    public MonthSummary monthSummary(UUID walletId, UUID ownerId, YearMonth month) {
        ZoneId zone = dailyLimitPolicy.zone();
        UUID accountId = requireOwned(walletId, ownerId).ledgerAccountId();
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        FlowTotals flows = ledger.flowsOf(accountId, start, end);
        return new MonthSummary(month, ledger.balanceAt(accountId, start), flows.moneyIn(), flows.moneyOut(),
                ledger.balanceAt(accountId, end));
    }

    /**
     * Closing balance of each of the last {@code days} days, today included, oldest first; days before the wallet
     * was opened are left out.
     */
    @Transactional(readOnly = true)
    public BalanceHistory balanceHistory(UUID walletId, UUID ownerId, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1");
        }
        Wallet wallet = requireOwned(walletId, ownerId);
        LocalDate today = dailyLimitPolicy.dayOf(clock.instant());
        LocalDate opened = dailyLimitPolicy.dayOf(wallet.createdAt());
        LocalDate from = today.minusDays(days - 1L);
        // One day more: the close of the day before the first is the balance the period opened with
        List<DailyBalance> closes = ledger.dailyBalancesOf(wallet.ledgerAccountId(),
                (from.isBefore(opened) ? opened : from).minusDays(1), today, dailyLimitPolicy.zone());
        return new BalanceHistory(closes.getFirst().balance(), closes.subList(1, closes.size()));
    }

    /**
     * The wallet's daily outgoing limit and what is left of it today.
     */
    @Transactional(readOnly = true)
    public WalletLimits limits(UUID walletId, UUID ownerId) {
        Wallet wallet = requireOwned(walletId, ownerId);
        Instant now = clock.instant();
        Money used = outgoingUsageRepository.usedOn(walletId, dailyLimitPolicy.dayOf(now), wallet.currency());
        Optional<Money> limit = dailyLimitPolicy.limitFor(wallet.currency());
        return new WalletLimits(limit.orElse(null), used,
                limit.map(max -> dailyLimitPolicy.remaining(max, used)).orElse(null),
                dailyLimitPolicy.nextDayStart(now));
    }

    @Override
    @Transactional(readOnly = true)
    public WalletInfo getOwnedWallet(UUID walletId, UUID ownerId) {
        return toInfo(requireOwned(walletId, ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WalletInfo> findWallet(UUID ownerId, Currency currency) {
        return walletRepository.findByOwnerAndCurrency(ownerId, currency).map(WalletService::toInfo);
    }

    @Override
    @Transactional
    public WalletInfo reserveOutgoing(UUID walletId, UUID ownerId, Money amount) {
        // The row lock serializes every outgoing operation of this wallet, and also waits for a freeze in progress
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .filter(found -> found.isOwnedBy(ownerId))
                .orElseThrow(WalletNotFoundException::new);
        if (!wallet.isActive()) {
            throw new WalletNotActiveException();
        }
        LocalDate today = dailyLimitPolicy.dayOf(clock.instant());
        dailyLimitPolicy.check(outgoingUsageRepository.usedOn(walletId, today, wallet.currency()), amount);
        outgoingUsageRepository.add(walletId, today, amount);
        return toInfo(wallet);
    }

    @Override
    @Transactional
    public void releaseOutgoing(UUID walletId, UUID ownerId, Money amount, Instant reservedAt) {
        walletRepository.findByIdForUpdate(walletId)
                .filter(found -> found.isOwnedBy(ownerId))
                .orElseThrow(WalletNotFoundException::new);
        outgoingUsageRepository.subtract(walletId, dailyLimitPolicy.dayOf(reservedAt), amount);
    }

    @Override
    @Transactional
    public WalletInfo acceptIncoming(UUID walletId, UUID ownerId) {
        Wallet wallet = lockForIncoming(walletId);
        if (!wallet.isOwnedBy(ownerId)) {
            throw new WalletNotFoundException();
        }
        return toInfo(wallet);
    }

    @Override
    @Transactional
    public void reserveTransfer(UUID sourceWalletId, UUID ownerId, UUID destinationWalletId, Money amount) {
        // Same fixed order as the ledger uses for accounts: A->B and B->A at the same time wait for each other
        if (sourceWalletId.compareTo(destinationWalletId) < 0) {
            reserveOutgoing(sourceWalletId, ownerId, amount);
            lockForIncoming(destinationWalletId);
        } else {
            lockForIncoming(destinationWalletId);
            reserveOutgoing(sourceWalletId, ownerId, amount);
        }
    }

    static WalletInfo toInfo(Wallet wallet) {
        return new WalletInfo(wallet.id(), wallet.ownerId(), wallet.currency(), wallet.ledgerAccountId(),
                wallet.isActive());
    }

    private Wallet lockForIncoming(UUID walletId) {
        Wallet wallet = walletRepository.findByIdForShare(walletId).orElseThrow(WalletNotFoundException::new);
        if (!wallet.isActive()) {
            throw new WalletNotActiveException();
        }
        return wallet;
    }

    private MovementFilter toFilter(MovementCriteria criteria) {
        ZoneId zone = dailyLimitPolicy.zone();
        return new MovementFilter(
                criteria.from() == null ? null : criteria.from().atStartOfDay(zone).toInstant(),
                criteria.to() == null ? null : criteria.to().plusDays(1).atStartOfDay(zone).toInstant(),
                criteria.types(), criteria.direction(), criteria.referencePrefix());
    }

    private Wallet requireOwned(UUID walletId, UUID ownerId) {
        return walletRepository.findById(walletId)
                .filter(wallet -> wallet.isOwnedBy(ownerId))
                .orElseThrow(WalletNotFoundException::new);
    }

    private WalletSummary toSummary(Wallet wallet) {
        return new WalletSummary(wallet.id(), ledger.balanceOf(wallet.ledgerAccountId()), wallet.status().name(),
                wallet.createdAt());
    }

    private static Currency parseCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnsupportedCurrencyException(String.valueOf(currencyCode));
        }
    }
}
