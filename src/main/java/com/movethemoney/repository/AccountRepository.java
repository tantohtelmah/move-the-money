package com.movethemoney.repository;

import com.movethemoney.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

// This interface extends JpaRepository, providing CRUD operations for the Account entity.
//"Give me database operations for Account, whose ID type is Long."
public interface AccountRepository extends JpaRepository<Account, Long> { 
}