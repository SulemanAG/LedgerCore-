package com.example.ledgercore.reconciliation;

import java.math.BigDecimal;

/**
 * Represents the result of reconciling an account balance
 * between PostgreSQL and Redis.
 *
 * <p>
 *     PostgreSQL is treated as the financial source of truth,
 *     while Redis is treated as the read-side projection.
 * </p>
 *
 * @param accountId account identifier
 * @param databaseBalance authoritative PostgreSQL balance
 * @param redisBalance projected Redis balance
 * @param status reconciliation status
 *
 * @author Suleman Agasimani
 * @since  1.0
 */
public record ReconciliationResult(
        Long accountId,
        BigDecimal databaseBalance,
        BigDecimal redisBalance,
        ReconciliationStatus status
) {
}
