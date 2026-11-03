package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.PaymentRequest;
import com.irvinsegura.wallet.payments.PaymentSession;
import com.irvinsegura.wallet.payments.Payments;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.DepositPolicy;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Top-up paid through the payment provider. A deposit is created pending, the payment is started once that commits,
 * and the money is credited only when the provider confirms it: right away with the simulated provider, after the
 * payer completes the checkout with a real one.
 */
@Service
public class DepositService {

    static final String ENTRY_TYPE = "DEPOSIT";

    private final Wallets wallets;
    private final UserDirectory users;
    private final DepositRepository depositRepository;
    private final IdempotentExecutor idempotentExecutor;
    private final Payments payments;
    private final DepositSettlement settlement;
    private final DepositPolicy depositPolicy;
    private final Clock clock;

    DepositService(Wallets wallets, UserDirectory users, DepositRepository depositRepository,
            IdempotentExecutor idempotentExecutor, Payments payments, DepositSettlement settlement,
            DepositPolicy depositPolicy, Clock clock) {
        this.wallets = wallets;
        this.users = users;
        this.depositRepository = depositRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.payments = payments;
        this.settlement = settlement;
        this.depositPolicy = depositPolicy;
        this.clock = clock;
    }

    /**
     * A replay returns the deposit as it is now, e.g. completed by the time the client retries.
     *
     * @throws PaymentProviderUnavailableException if the payment could not be started; the deposit is then failed
     */
    public Idempotent<DepositReceipt> deposit(DepositCommand command) {
        String requestHash = IdempotencyRecord.fingerprint(ENTRY_TYPE,
                command.walletId().toString(),
                command.amount().stripTrailingZeros().toPlainString());
        Idempotent<DepositReceipt> opened = idempotentExecutor.execute(command.userId(), command.idempotencyKey(),
                requestHash,
                depositId -> open(depositId, command),
                depositId -> DepositReceipt.from(depositRepository.findById(depositId).orElseThrow()));
        if (opened.replayed()) {
            return opened;
        }
        // After the commit: the provider is called over the network, never while holding locks
        return new Idempotent<>(startPayment(opened.value().id()), false);
    }

    @Transactional(readOnly = true)
    public DepositReceipt getDeposit(UUID userId, UUID walletId, UUID depositId) {
        return depositRepository.findById(depositId)
                .filter(deposit -> deposit.userId().equals(userId) && deposit.walletId().equals(walletId))
                .map(DepositReceipt::from)
                .orElseThrow(DepositNotFoundException::new);
    }

    private DepositReceipt open(UUID depositId, DepositCommand command) {
        // Refused here so the payer is never sent to a checkout for money that cannot be credited; checked again
        // when the payment succeeds
        users.authorizeDeposit(command.userId());
        WalletInfo wallet = wallets.acceptIncoming(command.walletId(), command.userId());
        Money amount = Money.of(command.amount(), wallet.currency().getCurrencyCode());
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        depositPolicy.check(amount);
        Deposit deposit = Deposit.open(depositId, command.userId(), wallet.id(), amount, clock.instant());
        depositRepository.save(deposit);
        // Same id for both, so the payment's outcome leads straight back to this deposit
        payments.register(new PaymentRequest(depositId, command.userId(), amount,
                "Deposit to your %s wallet".formatted(wallet.currency().getCurrencyCode())));
        return DepositReceipt.from(deposit);
    }

    private DepositReceipt startPayment(UUID depositId) {
        PaymentSession session;
        try {
            session = payments.start(depositId);
        } catch (PaymentProviderUnavailableException e) {
            settlement.abandon(depositId, DepositStatus.FAILED);
            throw e;
        }
        if (session.succeeded()) {
            return settlement.complete(depositId).orElseThrow();
        }
        return settlement.awaitPayment(depositId, session.checkoutUrl(), session.expiresAt());
    }
}
