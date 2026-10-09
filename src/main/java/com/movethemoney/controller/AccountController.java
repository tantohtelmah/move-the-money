package com.movethemoney.controller;

import com.movethemoney.dto.CreateAccountRequest;
import com.movethemoney.dto.AccountReconciliationResult;
import com.movethemoney.model.Account;
import com.movethemoney.service.AccountService;
import com.movethemoney.service.ReconciliationService;
import com.movethemoney.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.movethemoney.model.Transfer;
import java.util.List;


@RestController //this class is a REST controller that handles HTTP requests related to accounts
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransferService transferService;
    private final ReconciliationService reconciliationService;

    public AccountController(
            AccountService accountService,
            TransferService transferService,
            ReconciliationService reconciliationService) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.reconciliationService = reconciliationService;
    }

    @PostMapping //this method handles HTTP POST requests to create a new account
    @ResponseStatus(HttpStatus.CREATED) //this annotation indicates that the response status for this method will be 201 Created
    public Account openAccount(@RequestBody CreateAccountRequest request) {
        return accountService.openAccount(request.getStartingBalance());
    }

    @GetMapping("/{id}") //this method handles HTTP GET requests to retrieve an account by its ID
    public Account getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @GetMapping("/{id}/transactions")
    public List<Transfer> getTransactionHistory(@PathVariable Long id) {
        return transferService.getTransactionHistory(id);
    }

    @GetMapping("/{id}/reconciliation")
    public AccountReconciliationResult reconcile(@PathVariable Long id) {
        return reconciliationService.reconcile(id);
    }
}