package com.example.ledgercore.model;

/**
 * Represents the roles available within LedgerCore.
 *
 * <p>
 *     Roles are used by the Spring Security to determine which categories of the operations
 *     an authenticated user is allowed to perform. Resource onwership and financial business
 *     rules are enforced separately from role-based authorization.
 * </p>
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
