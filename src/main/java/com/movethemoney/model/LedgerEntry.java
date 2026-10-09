package com.movethemoney.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID journalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", updatable = false)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private LedgerAccount ledgerAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private LedgerEntryPurpose purpose;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            UUID journalId,
            Account account,
            Transfer transfer,
            LedgerAccount ledgerAccount,
            LedgerEntryType entryType,
            LedgerEntryPurpose purpose,
            BigDecimal amount) {
        this.journalId = journalId;
        this.account = account;
        this.transfer = transfer;
        this.ledgerAccount = ledgerAccount;
        this.entryType = entryType;
        this.purpose = purpose;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        validate();
    }

    public Long getId() {
        return id;
    }

    public UUID getJournalId() {
        return journalId;
    }

    public Account getAccount() {
        return account;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public LedgerAccount getLedgerAccount() {
        return ledgerAccount;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public LedgerEntryPurpose getPurpose() {
        return purpose;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PreUpdate
    @PreRemove
    private void preventMutation() {
        throw new IllegalStateException("Posted ledger entries are immutable");
    }

    private void validate() {
        if (journalId == null || ledgerAccount == null || entryType == null || purpose == null) {
            throw new IllegalArgumentException("Ledger entry fields must not be null");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() > 19 || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Ledger amount must be positive and fit NUMERIC(19, 2)");
        }
        if ((ledgerAccount == LedgerAccount.CUSTOMER) != (account != null)) {
            throw new IllegalArgumentException("Customer ledger entries require an account");
        }
        if (purpose == LedgerEntryPurpose.TRANSFER) {
            if (ledgerAccount != LedgerAccount.CUSTOMER || transfer == null) {
                throw new IllegalArgumentException("Transfer entries must reference a transfer and customer account");
            }
        } else if (transfer != null) {
            throw new IllegalArgumentException("Opening balance entries cannot reference a transfer");
        }
    }
}
