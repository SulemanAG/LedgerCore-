package com.example.ledgercore.outbox;

import com.example.ledgercore.model.Currency;

import java.math.BigDecimal;

/**
 * Event payload representing a completed withdrawal.
 *
 * <p>
 * This payload is stored in the transactional outbox and will
 * eventually be published to Kafka by the outbox relay.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public record WithdrawalEventPayload(
        Long transactionId,
        Long accountId,
        BigDecimal amount,
        Currency currency,
        String reference
) {
}