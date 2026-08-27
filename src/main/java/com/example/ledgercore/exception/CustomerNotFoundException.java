package com.example.ledgercore.exception;

/**
 * Thrown when a requested customer cannot be found in LedgerCore.
 *
 * <p>This exception represents a business-level resource lookup failure and
 * allows the service layer to communicate that the requested customer does
 * not exist without exposing persistence-specific exceptions to the
 * controller layer.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer not found with id: " + customerId);
    }
}