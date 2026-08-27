package com.example.ledgercore.exception;

/**
 * Thrown when a requested account cannot be found in LedgerCore.
 * This exception represents a business-level resource lookup failure and allows the service layer to communicate
 * that the requested account does not exist without exposing persistence-specific exceptions to the controller layer.
 * @author Suleman Agasimani
 * @since 1.0
 */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long accountId) {

        super("Account not found with accountId: "+accountId);
    }
}
