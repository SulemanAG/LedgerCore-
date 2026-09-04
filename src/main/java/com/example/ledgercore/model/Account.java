package com.example.ledgercore.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Represents a financial account maintained by LedgerCore.
 *
 * <p>An account belongs to exactly one customer and maintains its current
 * monetary balance in a specific currency. The account uses {@link BigDecimal}
 * for monetary values and {@code @Version} for optimistic concurrency control.</p>
 *
 * <p>The account balance represents the current balance available for
 * efficient access, while the double-entry ledger will eventually serve as
 * the authoritative record of financial movements. Account numbers are unique
 * business identifiers and are separate from the internal database identifier.</p>
 *
 * <p>Accounts have an independent lifecycle and should not be automatically
 * deleted through customer-account cascading. Financial accounts should
 * eventually be suspended or closed rather than physically deleted so that
 * their financial history remains auditable.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;


    @Column(
            name = "account_number",
            nullable = false,
            unique = true,
            length = 32
    )
    private String accountNumber;


    @Column(
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal balance = BigDecimal.ZERO;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private AccountType accountType;


    @Version
    private Long version;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "customer_id",
            nullable = false
    )
    private Customer customer;

    public Account() {
    }

    public Account(
            Long accountId,
            String accountNumber,
            BigDecimal balance,
            Currency currency,
            AccountStatus status,
            Long version,
            Customer customer,
            AccountType accountType
    ) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
        this.version = version;
        this.customer = customer;
        this.accountType=accountType;
    }
}