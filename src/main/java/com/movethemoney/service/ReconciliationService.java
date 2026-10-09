package com.movethemoney.service;

import com.movethemoney.dto.AccountReconciliationResult;
import com.movethemoney.model.Account;
import com.movethemoney.repository.AccountRepository;
import com.movethemoney.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ReconciliationService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountReconciliationResult reconcile(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.isLedgerInitialized()) {
            return new AccountReconciliationResult(
                    accountId,
                    AccountReconciliationResult.Status.LEDGER_NOT_INITIALIZED,
                    account.getBalance(),
                    null,
                    null);
        }

        BigDecimal ledgerBalance = calculateLedgerBalance(accountId);
        BigDecimal difference = account.getBalance().subtract(ledgerBalance);
        AccountReconciliationResult.Status status = difference.signum() == 0
                ? AccountReconciliationResult.Status.RECONCILED
                : AccountReconciliationResult.Status.DISCREPANCY;

        return new AccountReconciliationResult(
                accountId,
                status,
                account.getBalance(),
                ledgerBalance,
                difference);
    }

    BigDecimal calculateLedgerBalance(Long accountId) {
        return ledgerEntryRepository.calculateBalanceForAccount(accountId);
    }
}
