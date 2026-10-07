package com.movethemoney.repository;

import com.movethemoney.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey); //this method allows you to find a Transfer entity based on its idempotency key, which is useful for ensuring that duplicate transfer requests are not processed multiple times
    List<Transfer> findByFromAccount_IdOrToAccount_IdOrderByCreatedAtDesc(
            Long fromAccountId,
            Long toAccountId
    );
}