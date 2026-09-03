package com.example.ledgercore.exception;

/**
 * Thrown when a withdrawal request violates
 * a withdrawal-specific business rule.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public class InvalidWithdrawalException extends RuntimeException {

    public InvalidWithdrawalException(String message) {
        super(message);
    }
}