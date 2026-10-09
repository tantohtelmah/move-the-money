package com.movethemoney.service;

import com.movethemoney.model.Account;
import com.movethemoney.model.Transfer;
import com.movethemoney.repository.AccountRepository;
import com.movethemoney.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerPostingService ledgerPostingService;
    private final ReconciliationService reconciliationService;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerPostingService ledgerPostingService,
            ReconciliationService reconciliationService) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerPostingService = ledgerPostingService;
        this.reconciliationService = reconciliationService;
    }

    @Transactional //this annotation indicates that the method should be executed within a transaction, ensuring that all database operations are atomic and consistent
    public Transfer transfer(
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String idempotencyKey) {

        if (fromAccountId == null || toAccountId == null
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Account IDs and idempotency key are required");
        }

        Optional<Transfer> existing =
                transferRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Validate request
        if (amount == null || amount.signum() <= 0
                || amount.scale() > 2 || amount.precision() > 19
                || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Transfer amount must be positive and fit NUMERIC(19, 2)");
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        // Lock in a stable order to avoid deadlocks between opposite-direction transfers.
        Long firstId = Math.min(fromAccountId, toAccountId);// Math.min(1, 2) → 1
        Long secondId = Math.max(fromAccountId, toAccountId); // Math.min(1, 2) → 2

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Account fromAccount =
                fromAccountId.equals(first.getId()) ? first : second; // Determine which account is the source of the transfer based on the provided IDs

        Account toAccount =
                toAccountId.equals(first.getId()) ? first : second;

        if (!fromAccount.isLedgerInitialized() || !toAccount.isLedgerInitialized()) {
            throw new IllegalStateException("Both accounts require verified ledger initialization before transfers");
        }

        BigDecimal fromLedgerBalance = reconciliationService.calculateLedgerBalance(fromAccountId);
        BigDecimal toLedgerBalance = reconciliationService.calculateLedgerBalance(toAccountId);
        if (fromAccount.getBalance().compareTo(fromLedgerBalance) != 0
                || toAccount.getBalance().compareTo(toLedgerBalance) != 0) {
            throw new IllegalStateException("Account balance does not match its ledger; reconcile before transferring");
        }

        if (fromLedgerBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        fromAccount.setBalance(fromLedgerBalance.subtract(amount));

        toAccount.setBalance(toLedgerBalance.add(amount));

        Transfer transfer = new Transfer(
                idempotencyKey,
                fromAccount,
                toAccount,
                amount);

        transferRepository.saveAndFlush(transfer);
        ledgerPostingService.postTransfer(transfer);
        return transfer;
    }

    public List<Transfer> getTransactionHistory(Long accountId) {

        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Account not found");
        }

        return transferRepository
                .findByFromAccount_IdOrToAccount_IdOrderByCreatedAtDesc(
                        accountId,
                        accountId
                );
    }

}