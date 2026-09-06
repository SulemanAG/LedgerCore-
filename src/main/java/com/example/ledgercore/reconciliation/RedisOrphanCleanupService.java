package com.example.ledgercore.reconciliation;

import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Administrative service responsible for safely auditing and cleaning
 * orphaned Redis account balance projections.
 *
 * <p>
 * PostgreSQL is the authoritative source of truth. A Redis balance projection
 * is considered orphaned if its key exists in Redis (in the format
 * {@code ledgercore:account:{accountId}:balance}) but no corresponding account
 * record exists in PostgreSQL.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class RedisOrphanCleanupService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRedisService redisService;

    public RedisOrphanCleanupService(
            AccountRepository accountRepository,
            AccountBalanceRedisService redisService) {

        this.accountRepository = accountRepository;
        this.redisService = redisService;
    }

    /**
     * Identifies Redis balance projection account IDs whose accounts exist in PostgreSQL.
     *
     * @return set of valid account IDs present in both Redis and PostgreSQL
     */
    public Set<Long> findValidAccountIds() {
        Set<Long> allProjected = redisService.findProjectedAccountIds();
        Set<Long> valid = new HashSet<>();
        for (Long accountId : allProjected) {
            if (accountRepository.existsById(accountId)) {
                valid.add(accountId);
            }
        }
        return valid;
    }

    /**
     * Identifies Redis balance projection account IDs whose accounts DO NOT exist in PostgreSQL.
     *
     * @return set of confirmed orphaned account IDs
     */
    public Set<Long> findOrphanedAccountIds() {
        Set<Long> allProjected = redisService.findProjectedAccountIds();
        Set<Long> orphaned = new HashSet<>();
        for (Long accountId : allProjected) {
            if (!accountRepository.existsById(accountId)) {
                orphaned.add(accountId);
            }
        }
        return orphaned;
    }

    /**
     * Safely deletes only the Redis projections for account IDs that are confirmed
     * to be absent from PostgreSQL.
     *
     * @return list of account IDs whose Redis balance projections were deleted
     */
    public List<Long> cleanOrphanedProjections() {
        Set<Long> orphanIds = findOrphanedAccountIds();
        List<Long> deletedIds = new ArrayList<>();

        for (Long accountId : orphanIds) {
            // Extra safety guard: verify account is absent from PostgreSQL prior to deletion
            if (!accountRepository.existsById(accountId)) {
                redisService.deleteBalance(accountId);
                deletedIds.add(accountId);
            }
        }

        return deletedIds;
    }

    /**
     * Safely deletes specified account IDs from Redis after re-verifying their absence
     * in PostgreSQL.
     *
     * @param accountIdsToClean account IDs selected for cleanup
     * @return list of account IDs actually deleted
     */
    public List<Long> cleanSpecificOrphanedProjections(Set<Long> accountIdsToClean) {
        List<Long> deletedIds = new ArrayList<>();

        for (Long accountId : accountIdsToClean) {
            if (!accountRepository.existsById(accountId)) {
                redisService.deleteBalance(accountId);
                deletedIds.add(accountId);
            }
        }

        return deletedIds;
    }
}
