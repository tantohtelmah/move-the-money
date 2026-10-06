package com.movethemoney.model;

import jakarta.persistence.*;
import java.math.BigDecimal; //Amounts must be exact since double and float are not exact representations of decimal values

@Entity //This annotation specifies that the class is an entity and is mapped to a database table
@Table(name = "accounts")
public class Account {
    // Defines the database table structure for the Account entity, including the id and balance fields, along with their respective annotations for primary key generation and column constraints.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    protected Account() {
    }

    public Account(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    // Not doing setBalance because we don't want to allow arbitrary balance changes. 
    // Instead, we will have methods for deposit and withdraw that will handle the balance 
    // changes in a controlled manner.
}