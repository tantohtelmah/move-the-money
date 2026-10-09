package com.movethemoney.dto;

import java.math.BigDecimal;

public record AccountReconciliationResult(
        Long accountId,
        Status status,
        BigDecimal storedBalance,
        BigDecimal ledgerBalance,
        BigDecimal difference) {

    public enum Status {
        RECONCILED,
        DISCREPANCY,
        LEDGER_NOT_INITIALIZED
    }
}
