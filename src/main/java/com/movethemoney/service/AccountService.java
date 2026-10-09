package com.movethemoney.service;

import com.movethemoney.model.Account;
import com.movethemoney.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service //this class contain business logic and is a service component in the Spring framework
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerPostingService ledgerPostingService;

    public AccountService(AccountRepository accountRepository, LedgerPostingService ledgerPostingService) {
        this.accountRepository = accountRepository;
        this.ledgerPostingService = ledgerPostingService;
    }

    @Transactional
    public Account openAccount(BigDecimal startingBalance) {
        if (startingBalance == null || startingBalance.signum() < 0
                || startingBalance.scale() > 2 || startingBalance.precision() > 19
                || startingBalance.precision() - startingBalance.scale() > 17) {
            throw new IllegalArgumentException("Starting balance must be non-negative and fit NUMERIC(19, 2)");
        }

        Account account = new Account(startingBalance);
        accountRepository.save(account);
        ledgerPostingService.postOpeningBalance(account, startingBalance);
        account.markLedgerInitialized();
        return account;
    }

    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }
}