package com.example.ledgercore.dto.response;

import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents the API response for a processed financial transaction.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public record TransactionResponse(
        Long transactionId,
        BigDecimal amount,
        Currency currency,
        TransactionStatus status,
        LocalDateTime createdAt,
        String reference
) {
}