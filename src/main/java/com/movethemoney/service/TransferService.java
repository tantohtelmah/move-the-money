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

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional //this annotation indicates that the method should be executed within a transaction, ensuring that all database operations are atomic and consistent
    public Transfer transfer(
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String idempotencyKey) {

        // 1. Check whether this transfer was already processed
        Optional<Transfer> existing =
                transferRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Validate request
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Transfer amount cannot have more than 2 decimal places"
            );
        }

        // 3. Lock accounts in consistent order
        Long firstId = Math.min(fromAccountId, toAccountId);// Math.min(1, 2) → 1
        Long secondId = Math.max(fromAccountId, toAccountId); // Math.min(1, 2) → 2

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Account fromAccount =
                fromAccountId.equals(first.getId()) ? first : second; // Determine which account is the source of the transfer based on the provided IDs

        Account toAccount =
                toAccountId.equals(first.getId()) ? first : second;

        // 4. Check funds AFTER obtaining the lock
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        // 5. Move the money
        fromAccount.setBalance(
                fromAccount.getBalance().subtract(amount));

        toAccount.setBalance(
                toAccount.getBalance().add(amount));

        // 6. Record the transfer
        Transfer transfer = new Transfer(
                idempotencyKey,
                fromAccount,
                toAccount,
                amount);

        return transferRepository.save(transfer);
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