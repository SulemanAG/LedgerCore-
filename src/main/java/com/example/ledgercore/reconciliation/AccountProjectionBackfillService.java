package com.example.ledgercore.reconciliation;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for safely rebuilding missing Redis balance projections
 * from authoritative PostgreSQL account data.
 *
 * <p>
 * PostgreSQL is the authoritative financial source of truth. Redis is treated
 * strictly as a read-side projection.
 * </p>
 *
 * <p>
 * Backfill safety invariants:
 * <ul>
 *     <li>Identifies accounts present in PostgreSQL but missing from Redis ({@link ReconciliationStatus#MISSING_FROM_REDIS}).</li>
 *     <li>Obtains account balance exclusively from PostgreSQL via {@link AccountRepository}.</li>
 *     <li>Populates Redis via {@link AccountBalanceRedisService#setBalance(Long, java.math.BigDecimal)}.</li>
 *     <li>Does NOT overwrite existing projections (whether {@link ReconciliationStatus#MATCH} or {@link ReconciliationStatus#MISMATCH}).</li>
 *     <li>Does NOT delete or alter orphaned projections (orphan cleanup is handled separately by {@link RedisOrphanCleanupService}).</li>
 *     <li>Does NOT modify PostgreSQL records or generate database transactions/events.</li>
 * </ul>
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class AccountProjectionBackfillService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRedisService redisService;
    private final ReconciliationService reconciliationService;

    public AccountProjectionBackfillService(
            AccountRepository accountRepository,
            AccountBalanceRedisService redisService,
            ReconciliationService reconciliationService) {

        this.accountRepository = accountRepository;
        this.redisService = redisService;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Backfills a single PostgreSQL account's Redis balance projection if it is missing.
     *
     * @param accountId PostgreSQL account identifier
     * @return true if the projection was missing and successfully backfilled;
     *         false if the account does not exist in PostgreSQL or if a Redis projection already exists.
     */
    public boolean backfillAccount(Long accountId) {
        // 1. Verify PostgreSQL account exists
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            // PostgreSQL account does not exist. Do NOT create or alter Redis projection.
            // Do NOT delete any existing Redis key if present (orphan cleanup is separate).
            return false;
        }

        // 2. Check if Redis projection already exists
        if (redisService.getBalance(accountId) != null) {
            // Redis projection already exists (either MATCH or MISMATCH).
            // Do NOT overwrite existing projections per safety rules.
            return false;
        }

        // 3. Obtain balance exclusively from PostgreSQL and populate Redis
        Account account = accountOpt.get();
        redisService.setBalance(account.getAccountId(), account.getBalance());
        return true;
    }

    /**
     * Finds all PostgreSQL accounts whose Redis projection is missing
     * (ReconciliationStatus.MISSING_FROM_REDIS) and backfills their Redis balance projections.
     *
     * @return list of account IDs that were successfully backfilled
     */
    public List<Long> backfillAllMissingProjections() {
        List<ReconciliationResult> results = reconciliationService.reconcileAllAccounts();
        List<Long> backfilledAccountIds = new ArrayList<>();

        for (ReconciliationResult result : results) {
            if (result.status() == ReconciliationStatus.MISSING_FROM_REDIS) {
                Long accountId = result.accountId();
                boolean backfilled = backfillAccount(accountId);
                if (backfilled) {
                    backfilledAccountIds.add(accountId);
                }
            }
        }

        return backfilledAccountIds;
    }

    /**
     * Backfills a specific batch of account IDs if their Redis projections are missing.
     *
     * @param accountIds list of account IDs to inspect and backfill
     * @return list of account IDs actually backfilled
     */
    public List<Long> backfillAccounts(List<Long> accountIds) {
        List<Long> backfilledAccountIds = new ArrayList<>();
        if (accountIds == null) {
            return backfilledAccountIds;
        }

        for (Long accountId : accountIds) {
            boolean backfilled = backfillAccount(accountId);
            if (backfilled) {
                backfilledAccountIds.add(accountId);
            }
        }

        return backfilledAccountIds;
    }
}
