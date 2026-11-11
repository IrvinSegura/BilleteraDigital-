package com.irvinsegura.wallet.ledger.application;

import com.irvinsegura.wallet.ledger.CapitalContributed;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.ledger.UnsupportedCurrencyException;
import com.irvinsegura.wallet.ledger.domain.model.CapitalContributionExistsException;
import com.irvinsegura.wallet.ledger.domain.model.CustomerBalance;
import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import com.irvinsegura.wallet.ledger.domain.port.JournalEntryRepository;
import com.irvinsegura.wallet.ledger.domain.port.LedgerAccountRepository;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platform's own money: what its owners put into the settlement account, and the balances of every system
 * account. Since provider deposits wait in the clearing account until the provider pays them out, withdrawals are
 * paid from capital in the meantime.
 */
@Service
public class CapitalService {

    static final String ENTRY_TYPE = "CAPITAL_CONTRIBUTION";

    private final Ledger ledger;
    private final LedgerAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ApplicationEventPublisher events;

    CapitalService(Ledger ledger, LedgerAccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository, ApplicationEventPublisher events) {
        this.ledger = ledger;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.events = events;
    }

    /**
     * Records money the owners wired into the partner bank. Returns the settlement account afterwards.
     *
     * @param reference the wire's bank reference; each is recorded once
     * @throws CapitalContributionExistsException if the reference was already recorded
     */
    @Transactional
    public LedgerAccount contribute(UUID administratorId, String currencyCode, BigDecimal amount, String reference,
            String reason) {
        Currency currency = supportedCurrency(currencyCode);
        Money money = Money.of(amount, currency.getCurrencyCode());
        if (money.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        UUID settlement = ledger.settlementAccount(currency);
        UUID capital = systemAccount(LedgerAccount.PLATFORM_CAPITAL, currency).id();
        // Locked before the check, so the same reference sent twice at once waits here and is then refused
        accountRepository.findAllForUpdate(List.of(settlement, capital));
        if (journalEntryRepository.existsByTypeAndReference(ENTRY_TYPE, reference)) {
            throw new CapitalContributionExistsException();
        }
        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, reference, settlement, capital, money));
        events.publishEvent(new CapitalContributed(entry.journalEntryId(), money.minorUnits(),
                currency.getCurrencyCode(), reference, reason, administratorId, entry.postedAt()));
        return accountRepository.findById(settlement).orElseThrow();
    }

    /**
     * Every system account, by code and currency.
     */
    @Transactional(readOnly = true)
    public List<LedgerAccount> systemAccounts() {
        return accountRepository.findSystemAccounts();
    }

    /**
     * All the customers' money, by currency.
     */
    @Transactional(readOnly = true)
    public List<CustomerBalance> customerBalances() {
        return accountRepository.sumCustomerBalances();
    }

    private LedgerAccount systemAccount(String systemCode, Currency currency) {
        return accountRepository.findSystemAccount(systemCode, currency)
                .orElseThrow(() -> new UnsupportedCurrencyException(currency.getCurrencyCode()));
    }

    private static Currency supportedCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedCurrencyException(currencyCode);
        }
    }

}
