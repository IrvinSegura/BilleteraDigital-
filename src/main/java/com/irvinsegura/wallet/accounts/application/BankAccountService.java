package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.BankAccountAdded;
import com.irvinsegura.wallet.accounts.BankAccountInfo;
import com.irvinsegura.wallet.accounts.BankAccountNotFoundException;
import com.irvinsegura.wallet.accounts.BankAccountRemoved;
import com.irvinsegura.wallet.accounts.BankAccounts;
import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import com.irvinsegura.wallet.accounts.domain.model.BankAccountLimitReachedException;
import com.irvinsegura.wallet.accounts.domain.model.Clabe;
import com.irvinsegura.wallet.accounts.domain.port.BankAccountRepository;
import com.irvinsegura.wallet.identity.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bank accounts users withdraw to. Adding one is announced to the owner and audited: it is where money can be sent,
 * so it is the first thing someone who took over an account would change.
 */
@Service
public class BankAccountService implements BankAccounts {

    private final BankAccountRepository repository;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    BankAccountService(BankAccountRepository repository, UserDirectory users, ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @throws com.irvinsegura.wallet.accounts.domain.model.InvalidClabeException if the CLABE is malformed
     * @throws com.irvinsegura.wallet.identity.AccountDisabledException          if the account is disabled
     */
    @Transactional
    public BankAccountSummary add(UUID ownerId, String clabe, String holderName) {
        Clabe parsed = Clabe.parse(clabe);
        users.authorizeAccountChange(ownerId);
        if (repository.findActiveByOwner(ownerId).size() >= BankAccount.MAX_ACTIVE_PER_OWNER) {
            throw new BankAccountLimitReachedException(BankAccount.MAX_ACTIVE_PER_OWNER);
        }
        Instant now = clock.instant();
        BankAccount bankAccount = BankAccount.register(ownerId, parsed, holderName, now);
        repository.save(bankAccount);
        events.publishEvent(new BankAccountAdded(bankAccount.id(), ownerId, parsed.bankCode(), parsed.lastFour(),
                now));
        return BankAccountSummary.from(bankAccount);
    }

    @Transactional(readOnly = true)
    public List<BankAccountSummary> list(UUID ownerId) {
        return repository.findActiveByOwner(ownerId).stream().map(BankAccountSummary::from).toList();
    }

    /**
     * Withdrawals already on their way still pay into it.
     */
    @Transactional
    public void remove(UUID ownerId, UUID bankAccountId) {
        BankAccount bankAccount = requireActive(bankAccountId, ownerId);
        Instant now = clock.instant();
        bankAccount.remove(now);
        repository.save(bankAccount);
        events.publishEvent(new BankAccountRemoved(bankAccountId, ownerId, bankAccount.clabe().lastFour(), now));
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccountInfo getActiveBankAccount(UUID bankAccountId, UUID ownerId) {
        return toInfo(requireActive(bankAccountId, ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccountInfo getBankAccount(UUID bankAccountId) {
        return toInfo(repository.findById(bankAccountId).orElseThrow(BankAccountNotFoundException::new));
    }

    // Someone else's account answers like a missing one, so ids cannot be probed
    private BankAccount requireActive(UUID bankAccountId, UUID ownerId) {
        return repository.findById(bankAccountId)
                .filter(found -> found.isOwnedBy(ownerId) && found.isActive())
                .orElseThrow(BankAccountNotFoundException::new);
    }

    private static BankAccountInfo toInfo(BankAccount bankAccount) {
        return new BankAccountInfo(bankAccount.id(), bankAccount.ownerId(), bankAccount.clabe().value(),
                bankAccount.holderName());
    }
}
