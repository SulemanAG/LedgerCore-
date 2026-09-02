package com.example.ledgercore.model;

/**
 * Defines the role of an account within LedgerCore.
 *
 * <p>
 *     CUSTOMER accounts belong to individual customers and are used for
 *     normal banking operations.
 * </p>
 *
 * <p>
 *     SYSTEM accounts represent ledgerCore-controlled accounts used for
 *     operations such as deposits, withdrawals, fees, and other
 *     external financial movements.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

public enum AccountType {

    //Normal customer-owned banking account.
    CUSTOMER,

    //Internal LedgerCore/system used account.
    SYSTEM
}
