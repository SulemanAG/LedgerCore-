package com.example.ledgercore.model;


/**
 * Represents the accounting side of a ledger entry.
 *
 * <P>
 *     Every financial transfer must contain at least one DEBIT entry
 *     and one CREDIT entry
 * </P>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public enum LedgerEntryType {

    DEBIT,

    CREDIT
}
