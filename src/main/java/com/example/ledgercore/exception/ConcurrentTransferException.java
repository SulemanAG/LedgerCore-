package com.example.ledgercore.exception;

/**
 * <p>Thrown when an account is modified by another transaction
 * while the current financial operation is being processed.
 * </p>
 *
 * <p>
 * This protects account balances from being overwritten by
 * concurrent transactions.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public class ConcurrentTransferException extends RuntimeException {

    public ConcurrentTransferException(String message) {
        super(message);
    }
}