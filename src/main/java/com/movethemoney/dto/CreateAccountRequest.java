package com.movethemoney.dto;

import java.math.BigDecimal;

public class CreateAccountRequest {

    private BigDecimal startingBalance;

    public BigDecimal getStartingBalance() {
        return startingBalance;
    }

    public void setStartingBalance(BigDecimal startingBalance) {
        this.startingBalance = startingBalance;
    }
}