package com.example.ledgercore.exception;

/**
 * Thrown when an authentication username is already registered.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Username already exists: " + username);
    }
}