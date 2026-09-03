package com.example.ledgercore.repository;

import com.example.ledgercore.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    List<Transaction> findDistinctByLedgerEntriesAccountAccountId(
            Long accountId
    );
}