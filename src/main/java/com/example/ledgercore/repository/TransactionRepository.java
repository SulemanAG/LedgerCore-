package com.example.ledgercore.repository;

import com.example.ledgercore.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 1. FIND ALL TRANSACTIONS INVOLVING AN ACCOUNT
    List<Transaction> findDistinctByLedgerEntriesAccountAccountId(Long accountId);

    // 2. FIND TRANSACTION USING IDEMPOTENCY KEY
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // 3. LOCK IDEMPOTENCY KEY UNTIL CURRENT TRANSACTION COMPLETES
    @Query(
            value = "SELECT pg_advisory_xact_lock(hashtext(:idempotencyKey))",
            nativeQuery = true
    )
    void lockIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey
    );
}