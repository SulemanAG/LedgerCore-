package com.example.ledgercore.exception;

/**
 * Thrown when a requested transaction cannot be found.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(Long transactionId) {
        super("Transaction not found with ID: " + transactionId);
    }
}