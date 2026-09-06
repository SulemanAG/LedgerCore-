package com.example.ledgercore.reconciliation;

import java.math.BigDecimal;

/**
 * Represents the result of reconciling an account balance
 * between PostgreSQL and Redis.
 *
 * <p>
 * For an orphaned Redis projection, {@code databaseBalance}
 * is {@code null} because the corresponding PostgreSQL account
 * does not exist.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public record ReconciliationResult(
        Long accountId,
        BigDecimal databaseBalance,
        BigDecimal redisBalance,
        ReconciliationStatus status
) {
}