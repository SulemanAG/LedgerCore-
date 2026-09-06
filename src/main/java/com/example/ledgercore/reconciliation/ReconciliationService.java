package com.example.ledgercore.reconciliation;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciles PostgreSQL account balances against Redis projections.
 *
 * <p>
 * PostgreSQL remains the authoritative source of financial state.
 * Redis is only a read-side projection and therefore must not be
 * treated as the source of truth during reconciliation.
 * </p>
 *
 * <p>
 * The service detects discrepancies but does not automatically
 * modify either PostgreSQL or Redis. This keeps reconciliation
 * safe and prevents an incorrect projection from becoming a
 * source of financial corruption.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class ReconciliationService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRedisService redisService;

    /**
     * Creates the reconciliation service.
     *
     * @param accountRepository repository containing authoritative
     *                          PostgreSQL account balances
     * @param redisService service providing Redis balance projections
     */
    public ReconciliationService(
            AccountRepository accountRepository,
            AccountBalanceRedisService redisService
    ) {
        this.accountRepository = accountRepository;
        this.redisService = redisService;
    }

    /**
     * Reconciles a single account.
     *
     * <p>
     * PostgreSQL is read first because it is the source of truth.
     * The corresponding Redis projection is then retrieved and
     * compared with the database balance.
     * </p>
     *
     * @param accountId account identifier
     * @return reconciliation result
     */
    public ReconciliationResult reconcileAccount(
            Long accountId
    ) {

        // 1. Retrieve the authoritative PostgreSQL account.
        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found: "
                                                + accountId
                                )
                        );

        BigDecimal databaseBalance =
                account.getBalance();

        // 2. Retrieve the Redis balance projection.
        BigDecimal redisBalance =
                redisService.getBalance(accountId);

        // 3. Redis projection does not exist.
        if (redisBalance == null) {

            return new ReconciliationResult(
                    accountId,
                    databaseBalance,
                    null,
                    ReconciliationStatus.MISSING_FROM_REDIS
            );
        }

        // 4. Compare PostgreSQL and Redis balances.
        if (databaseBalance.compareTo(redisBalance) == 0) {

            return new ReconciliationResult(
                    accountId,
                    databaseBalance,
                    redisBalance,
                    ReconciliationStatus.MATCH
            );
        }

        // 5. A discrepancy exists.
        return new ReconciliationResult(
                accountId,
                databaseBalance,
                redisBalance,
                ReconciliationStatus.MISMATCH
        );
    }

    /**
     * Reconciles every account currently stored in PostgreSQL.
     *
     * <p>
     * Every account is checked independently. The method does not
     * modify either PostgreSQL or Redis.
     * </p>
     *
     * @return reconciliation results for all accounts
     */
    public List<ReconciliationResult> reconcileAllAccounts() {

        // 1. Retrieve all authoritative accounts.
        List<Account> accounts =
                accountRepository.findAll();

        // 2. Prepare the reconciliation result list.
        List<ReconciliationResult> results =
                new ArrayList<>();

        // 3. Reconcile each account individually.
        for (Account account : accounts) {

            results.add(
                    reconcileAccount(
                            account.getAccountId()
                    )
            );
        }

        return results;
    }
}