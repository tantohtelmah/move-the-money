package com.movethemoney.model;

import jakarta.persistence.*; //uesd for mapping Java objects to database tables and vice versa(JPA - Java Persistence API)
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "transfers",
    uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key")
)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Transfer() {
    }

    public Transfer(
            String idempotencyKey,
            Account fromAccount,
            Account toAccount,
            BigDecimal amount) {

        this.idempotencyKey = idempotencyKey;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}