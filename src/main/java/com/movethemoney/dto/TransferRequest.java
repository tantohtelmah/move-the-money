package com.movethemoney.dto;

import java.math.BigDecimal;

public class TransferRequest { //this class is a Data Transfer Object (DTO) that represents a request to transfer money between two accounts. It contains the necessary information for the transfer, including the source account ID, destination account ID, and the amount to be transferred.

    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(Long fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(Long toAccountId) {
        this.toAccountId = toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}