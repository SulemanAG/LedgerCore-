package com.example.ledgercore.reconciliation;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for reconciling PostgreSQL account balances
 * against Redis balance projections.
 *
 * <p>
 * PostgreSQL is treated as the authoritative source of account
 * and balance information. Redis is treated as a read-side
 * projection.
 * </p>
 *
 * <p>
 * Reconciliation works in both directions:
 * </p>
 *
 * <ol>
 *     <li>PostgreSQL → Redis: detects missing projections and balance mismatches.</li>
 *     <li>Redis → PostgreSQL: detects orphaned Redis projections.</li>
 * </ol>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class ReconciliationService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRedisService redisService;

    public ReconciliationService(
            AccountRepository accountRepository,
            AccountBalanceRedisService redisService) {

        this.accountRepository = accountRepository;
        this.redisService = redisService;
    }

    /**
     * Reconciles a single PostgreSQL account against its Redis projection.
     *
     * @param accountId PostgreSQL account identifier
     * @return reconciliation result
     */
    public ReconciliationResult reconcileAccount(Long accountId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + accountId));

        BigDecimal databaseBalance = account.getBalance();
        BigDecimal redisBalance = redisService.getBalance(accountId);

        if (redisBalance == null) {

            return new ReconciliationResult(
                    accountId,
                    databaseBalance,
                    null,
                    ReconciliationStatus.MISSING_FROM_REDIS
            );
        }

        if (databaseBalance.compareTo(redisBalance) == 0) {

            return new ReconciliationResult(
                    accountId,
                    databaseBalance,
                    redisBalance,
                    ReconciliationStatus.MATCH
            );
        }

        return new ReconciliationResult(
                accountId,
                databaseBalance,
                redisBalance,
                ReconciliationStatus.MISMATCH
        );
    }

    /**
     * Reconciles all PostgreSQL accounts and detects orphaned
     * Redis projections.
     *
     * @return complete reconciliation result list
     */
    public List<ReconciliationResult> reconcileAllAccounts() {

        List<ReconciliationResult> results = new ArrayList<>();

        /*
         * 1. PostgreSQL → Redis
         *
         * Every PostgreSQL account must have a corresponding
         * Redis balance projection.
         */
        List<Account> accounts = accountRepository.findAll();

        Set<Long> databaseAccountIds = new HashSet<>();

        for (Account account : accounts) {

            Long accountId = account.getAccountId();

            databaseAccountIds.add(accountId);

            results.add(reconcileAccount(accountId));
        }

        /*
         * 2. Redis → PostgreSQL
         *
         * Every Redis balance projection should correspond
         * to an existing PostgreSQL account.
         */
        Set<Long> redisAccountIds =
                redisService.findProjectedAccountIds();

        for (Long redisAccountId : redisAccountIds) {

            if (!databaseAccountIds.contains(redisAccountId)) {

                BigDecimal redisBalance =
                        redisService.getBalance(redisAccountId);

                results.add(
                        new ReconciliationResult(
                                redisAccountId,
                                null,
                                redisBalance,
                                ReconciliationStatus.ORPHANED_IN_REDIS
                        )
                );
            }
        }

        return results;
    }
}