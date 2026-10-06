package com.movethemoney.service;

import com.movethemoney.model.Account;
import com.movethemoney.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service //this class contain business logic and is a service component in the Spring framework
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account openAccount(BigDecimal startingBalance) {
        // Validate the starting balance to ensure it is not negative
        if (startingBalance == null || startingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Starting balance cannot be negative");
        }

        Account account = new Account(startingBalance);
        return accountRepository.save(account); //this creates jva Account, save and insert into PostgreSQL
    }

    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }
}