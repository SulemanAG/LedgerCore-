package com.example.ledgercore.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a financial transaction processed by LedgerCore.
 *
 * <p>
 *     A transaction represents the business event of moving money.
 *     The actual accounting representation of that movement is maintained
 *     through associated {@link LedgerEntry} records.
 * </p>
 *
 * <p>
 *     For a transfer, the transaction will normally contain one debit ledger
 *     entry and one credit ledger entry.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Getter
@Setter
@Entity
@Table(name="transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Column(nullable = false,precision = 19,scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private TransactionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    //Human-readable reference associated with transaction, it is optional
    @Column(length = 100)
    private String reference;


    //Idempotency Key
    @Column(name="idempotency_key",unique = true,length =100)
    private String idempotencyKey;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();



    public Transaction() {
    }

    public Transaction(Long transactionId, BigDecimal amount,
                       Currency currency, TransactionStatus status,
                       LocalDateTime createdAt, String reference) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.reference = reference;
    }
}
