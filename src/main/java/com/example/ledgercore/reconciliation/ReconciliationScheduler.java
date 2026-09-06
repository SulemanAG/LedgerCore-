package com.example.ledgercore.reconciliation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled component responsible for periodically reconciling
 * PostgreSQL account balances against Redis balance projections.
 *
 * <p>
 * PostgreSQL remains the authoritative source of account balances,
 * while Redis contains the read-side balance projection.
 * </p>
 *
 * <p>
 * The scheduler performs reconciliation in both directions and
 * reports missing, mismatched, and orphaned projections.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Component
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(
            ReconciliationService reconciliationService) {

        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            fixedDelayString =
                    "${ledgercore.reconciliation.fixed-delay}"
    )
    public void reconcileAllAccounts() {

        System.out.println(
                "RECONCILIATION: starting account reconciliation"
        );

        List<ReconciliationResult> results =
                reconciliationService.reconcileAllAccounts();

        int matchCount = 0;
        int mismatchCount = 0;
        int missingFromRedisCount = 0;
        int orphanedInRedisCount = 0;

        for (ReconciliationResult result : results) {

            if (result.status() == ReconciliationStatus.MATCH) {

                matchCount++;

            } else if (result.status() ==
                    ReconciliationStatus.MISMATCH) {

                mismatchCount++;

                System.out.println(
                        "RECONCILIATION: MISMATCH detected"
                                + " | accountId=" + result.accountId()
                                + " | databaseBalance="
                                + result.databaseBalance()
                                + " | redisBalance="
                                + result.redisBalance()
                );

            } else if (result.status() ==
                    ReconciliationStatus.MISSING_FROM_REDIS) {

                missingFromRedisCount++;

                System.out.println(
                        "RECONCILIATION: Redis projection missing"
                                + " | accountId=" + result.accountId()
                );

            } else if (result.status() ==
                    ReconciliationStatus.ORPHANED_IN_REDIS) {

                orphanedInRedisCount++;

                System.out.println(
                        "RECONCILIATION: Orphaned Redis projection"
                                + " | accountId=" + result.accountId()
                                + " | redisBalance="
                                + result.redisBalance()
                );
            }
        }

        System.out.println(
                "RECONCILIATION: completed"
                        + " | totalResults=" + results.size()
                        + " | matches=" + matchCount
                        + " | mismatches=" + mismatchCount
                        + " | missingFromRedis="
                        + missingFromRedisCount
                        + " | orphanedInRedis="
                        + orphanedInRedisCount
        );
    }
}