package com.example.ledgercore.model;


import com.sun.jdi.NativeMethodException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Represents one accounting entry belonging to a financial transaction.
 *
 * <p>
 *     Ledger entries form the accounting record of the money movement.
 *     A transfer between two accounts produces a debit entry and a
 *     corresponding Credit entry.
 * </p>
 *
 * <p>
 *     The ledger entry does not independently represent a transaction.
 *     It belongs to exactly one {@link Transaction}.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@Getter
@Setter
@Entity
@Table(name = "Ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ledgerEntryId;

    @Column(nullable = false,precision = 19,scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private LedgerEntryType entryType;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "transaction_id",nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "account_id",nullable = false)
    private Account account;

    public LedgerEntry(){

    }

    public LedgerEntry(Long ledgerEntryId, BigDecimal amount,
                       LedgerEntryType entryType, Transaction transaction,
                       Account account) {
        this.ledgerEntryId = ledgerEntryId;
        this.amount = amount;
        this.entryType = entryType;
        this.transaction = transaction;
        this.account = account;
    }
}
