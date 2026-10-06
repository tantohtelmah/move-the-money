package com.movethemoney.repository;

import com.movethemoney.model.Account;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// This interface extends JpaRepository, providing CRUD operations for the Account entity.
//"Give me database operations for Account, whose ID type is Long."
public interface AccountRepository extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    //JPQL
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    // This method retrieves an Account entity by its ID and 
    // applies a pessimistic write lock to prevent concurrent modifications.
    // "Give me Account X and lock it because I'm about to modify it".
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}