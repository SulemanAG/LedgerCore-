package com.example.ledgercore.reconciliation;

/**
 * Represents the result of comparing an account's
 * PostgreSQL balance with its Redis projection.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public enum ReconciliationStatus {

    /**
     * PostgreSQL and Redis contain the same balance.
     */
    MATCH,

    /**
     * PostgreSQL and Redis contain different balances.
     */
    MISMATCH,

    /**
     * PostgreSQL contains an account but Redis does not
     * contain a corresponding projection.
     */
    MISSING_FROM_REDIS
}