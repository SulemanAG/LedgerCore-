package com.example.ledgercore.dto.response;

import com.example.ledgercore.model.LedgerEntryType;

import java.math.BigDecimal;

/**
 * Response object representing a single ledger entry.
 *
 * <p>
 * Exposes the financial movement associated with an account
 * without exposing the JPA entity directly.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public record LedgerEntryResponse(
        Long ledgerEntryId,
        BigDecimal amount,
        LedgerEntryType entryType,
        Long accountId
) {
}