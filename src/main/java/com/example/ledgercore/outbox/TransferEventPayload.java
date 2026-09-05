package com.example.ledgercore.outbox;

import com.example.ledgercore.model.Currency;

import java.math.BigDecimal;

public record TransferEventPayload(
        Long transactionId,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        Currency currency,
        String reference,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter
) {}