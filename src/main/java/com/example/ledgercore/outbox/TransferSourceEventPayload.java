package com.example.ledgercore.outbox;

import com.example.ledgercore.model.Currency;

import java.math.BigDecimal;

/**
 * Event payload representing the source account debit side of a completed transfer.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public record TransferSourceEventPayload(
        Long transactionId,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        Currency currency,
        String reference,
        BigDecimal sourceBalanceAfter,
        Long sourceAccountVersion
) {}
