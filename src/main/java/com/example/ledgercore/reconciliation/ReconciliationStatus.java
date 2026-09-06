package com.example.ledgercore.reconciliation;

/**
 * Represents the possible outcomes of reconciling
 * PostgreSQL account balances against Redis projections.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public enum ReconciliationStatus {

    /**
     * PostgreSQL and Redis contain the account
     * and their balances are equal.
     */
    MATCH,

    /**
     * PostgreSQL and Redis contain the account,
     * but their balances are different.
     */
    MISMATCH,

    /**
     * The account exists in PostgreSQL but its
     * Redis balance projection does not exist.
     */
    MISSING_FROM_REDIS,

    /**
     * A Redis balance projection exists, but the
     * corresponding PostgreSQL account no longer exists.
     */
    ORPHANED_IN_REDIS
}