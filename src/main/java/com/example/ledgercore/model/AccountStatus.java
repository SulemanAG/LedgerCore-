package com.example.ledgercore.model;

/**
 * Represents the lifecycle state of a LedgerCore account
 *
 * <p>
 *     An account may be active for normal operations, frozen to temporarily
 *     restrict financial activity, or closed when its lifecycle has ended.
 *     Closed accounts are retained rather than physically deleted so that
 *     financial history remains auditable.
 * </p>
 * @author Suleman Agasimani
 * @since 1.0
 */
public enum AccountStatus {

    ACTIVE,
    FROZEN,
    CLOSED
}
