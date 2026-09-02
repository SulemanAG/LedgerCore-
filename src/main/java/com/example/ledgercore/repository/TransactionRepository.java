package com.example.ledgercore.repository;

import com.example.ledgercore.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {
}