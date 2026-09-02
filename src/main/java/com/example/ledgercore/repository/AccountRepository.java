package com.example.ledgercore.repository;

import com.example.ledgercore.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository responsible for persistence operations on LedgerCore accounts.
 *
 * <p>Spring Data JPA provides the standard CRUD operations automatically.
 * Account-specific queries will be added here as the financial domain
 * requires them, such as account-number lookup and active-account checks.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String AccountNumber);
}