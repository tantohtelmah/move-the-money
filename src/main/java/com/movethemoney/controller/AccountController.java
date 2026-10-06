package com.movethemoney.controller;

import com.movethemoney.dto.CreateAccountRequest;
import com.movethemoney.model.Account;
import com.movethemoney.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController //this class is a REST controller that handles HTTP requests related to accounts
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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
}