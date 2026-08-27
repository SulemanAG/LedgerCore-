package com.example.ledgercore.model;

/**
 * Represent the supported currencies for LedgerCore accounts
 *
 * <p>
 *     The supported currencies are intentionally restricted to known set so that
 *     accounts cannot be created with arbitary or invalid currency
 *     codes.
 * </p>
 * @author Suleman Agasimani
 * @since 1.0
 */
public enum Currency {
    INR,
    USD,
    EUR
}
