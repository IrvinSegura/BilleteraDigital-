package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.OperationSuggestion;
import com.irvinsegura.wallet.transfers.domain.model.OperationDailyTotal;
import com.irvinsegura.wallet.transfers.domain.model.OperationTotal;
import com.irvinsegura.wallet.transfers.domain.model.PendingOperation;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.model.TransferNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalNotFoundException;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import com.irvinsegura.wallet.transfers.domain.port.OperationOverview;
import com.irvinsegura.wallet.transfers.domain.port.OperationSuggestions;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Any operation by id, whoever made it, for support questions that quote an id from a statement or the audit log.
 * Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class OperationAdministrationService {

    private static final int MAX_SUGGESTIONS = 6;

    private final TransferRepository transferRepository;
    private final DepositRepository depositRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final OperationSuggestions suggestions;
    private final OperationOverview overview;
    private final Clock clock;

    private final ZoneId usersZone;

    OperationAdministrationService(TransferRepository transferRepository, DepositRepository depositRepository,
            WithdrawalRepository withdrawalRepository, OperationSuggestions suggestions, OperationOverview overview,
            Clock clock, @Value("${wallet.accounts.limit-zone}") ZoneId usersZone) {
        this.usersZone = usersZone;
        this.transferRepository = transferRepository;
        this.depositRepository = depositRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.suggestions = suggestions;
        this.overview = overview;
        this.clock = clock;
    }

    /**
     * Deposits and withdrawals still waiting, the oldest first, so support sees the stuck ones at the top.
     *
     * @param kind {@code deposit} or {@code withdrawal}; null for both
     */
    @Transactional(readOnly = true)
    public List<PendingOperation> pending(String kind, int limit) {
        return overview.findPending(kind, limit);
    }

    /**
     * What finished in the last {@code days} days, by kind and currency.
     */
    @Transactional(readOnly = true)
    public List<OperationTotal> totals(int days) {
        return overview.totalsSince(clock.instant().minus(Duration.ofDays(days)));
    }

    /**
     * What finished in each of the last {@code days} days, today included, by the days the users live in.
     */
    @Transactional(readOnly = true)
    public List<OperationDailyTotal> dailyTotals(int days) {
        LocalDate first = LocalDate.now(clock.withZone(usersZone)).minusDays(days - 1L);
        return overview.dailyTotalsSince(first.atStartOfDay(usersZone).toInstant(), usersZone);
    }

    /**
     * A preview of the operations whose id starts with what is being typed.
     *
     * @param kind {@code transfer}, {@code deposit} or {@code withdrawal}; null for any
     */
    @Transactional(readOnly = true)
    public List<OperationSuggestion> suggest(String idStart, String kind) {
        return suggestions.find(idStart.toLowerCase(Locale.ROOT), kind, MAX_SUGGESTIONS);
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID transferId) {
        return transferRepository.findById(transferId).orElseThrow(TransferNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Deposit getDeposit(UUID depositId) {
        return depositRepository.findById(depositId).orElseThrow(DepositNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Withdrawal getWithdrawal(UUID withdrawalId) {
        return withdrawalRepository.findById(withdrawalId).orElseThrow(WithdrawalNotFoundException::new);
    }
}
