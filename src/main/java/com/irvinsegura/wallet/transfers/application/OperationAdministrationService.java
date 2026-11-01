package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.model.TransferNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalNotFoundException;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Any operation by id, whoever made it, for support questions that quote an id from a statement or the audit log.
 * Access is restricted to the ADMIN role by the security configuration.
 */
@Service
public class OperationAdministrationService {

    private final TransferRepository transferRepository;
    private final DepositRepository depositRepository;
    private final WithdrawalRepository withdrawalRepository;

    OperationAdministrationService(TransferRepository transferRepository, DepositRepository depositRepository,
            WithdrawalRepository withdrawalRepository) {
        this.transferRepository = transferRepository;
        this.depositRepository = depositRepository;
        this.withdrawalRepository = withdrawalRepository;
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
