package com.example.ledgercore.model;

/**
 * Represents the LifeCycle state of a financial transaction.
 *
 * <p>
 *     A transaction is initially created in the PENDING state and becomes
 *     COMPLETED only after all corresponding ledger entries and account
 *     balance updates have successfully been persisted.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */


public enum TransactionStatus
{

    PENDING,


    COMPLETED,

    FAILED


}
