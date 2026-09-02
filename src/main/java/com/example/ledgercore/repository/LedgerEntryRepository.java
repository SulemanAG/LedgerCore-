package com.example.ledgercore.repository;

import com.example.ledgercore.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountAccountId(Long accountId);

    List<LedgerEntry> findByTransactionTransactionId(Long transactionId);
}