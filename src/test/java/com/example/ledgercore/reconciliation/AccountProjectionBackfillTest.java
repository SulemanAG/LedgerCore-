package com.example.ledgercore.reconciliation;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for safe Redis account balance projection backfill / recovery.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class AccountProjectionBackfillTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountBalanceRedisService redisService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountProjectionBackfillService backfillService;

    private Customer testCustomer;
    private final List<Account> createdAccounts = new ArrayList<>();
    private final List<Long> createdRedisOnlyAccountIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setCustomerName("Backfill Test Customer");
        testCustomer.setCustomerAddress("456 Recovery Lane");
        testCustomer.setCustomerPhoneNumber("7777777777");
        testCustomer.setCustomerEmail("backfill.test." + System.nanoTime() + "@test.com");
        testCustomer = customerRepository.saveAndFlush(testCustomer);
    }

    @AfterEach
    void tearDown() {
        // Clean up created Redis projections
        for (Account account : createdAccounts) {
            if (account.getAccountId() != null) {
                redisService.deleteBalance(account.getAccountId());
            }
        }
        for (Long redisOnlyId : createdRedisOnlyAccountIds) {
            redisService.deleteBalance(redisOnlyId);
        }

        // Clean up created PostgreSQL accounts
        for (Account account : createdAccounts) {
            if (account.getAccountId() != null) {
                accountRepository.deleteById(account.getAccountId());
            }
        }
        createdAccounts.clear();
        createdRedisOnlyAccountIds.clear();

        // Clean up created PostgreSQL customer
        if (testCustomer != null && testCustomer.getCustomerId() != null) {
            customerRepository.deleteById(testCustomer.getCustomerId());
        }
    }

    private Account createTestAccount(BigDecimal initialBalance) {
        Account account = new Account();
        account.setAccountNumber("BACKFILL" + System.nanoTime());
        account.setBalance(initialBalance);
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.CUSTOMER);
        account.setCustomer(testCustomer);
        account = accountRepository.saveAndFlush(account);
        createdAccounts.add(account);
        return account;
    }

    @Test
    void shouldBackfillSingleAccountWhenMissingFromRedis() {
        Account account = createTestAccount(new BigDecimal("2500.50"));
        Long accountId = account.getAccountId();

        // Ensure Redis projection is initially missing
        redisService.deleteBalance(accountId);
        assertNull(redisService.getBalance(accountId), "Redis projection should initially be null");

        // Execute backfill
        boolean result = backfillService.backfillAccount(accountId);
        assertTrue(result, "backfillAccount should return true for missing projection");

        // Verify Redis projection matches PostgreSQL balance exactly
        BigDecimal redisBalance = redisService.getBalance(accountId);
        assertNotNull(redisBalance, "Redis projection should be populated");
        assertEquals(0, new BigDecimal("2500.50").compareTo(redisBalance),
                "Redis balance should match PostgreSQL balance");
    }

    @Test
    void shouldNotOverwriteExistingValidRedisProjection() {
        Account account = createTestAccount(new BigDecimal("3000.00"));
        Long accountId = account.getAccountId();

        // Pre-populate matching Redis projection
        redisService.setBalance(accountId, new BigDecimal("3000.00"));

        // Execute backfill
        boolean result = backfillService.backfillAccount(accountId);
        assertFalse(result, "backfillAccount should return false when Redis projection already exists");

        // Verify balance in Redis was not altered
        assertEquals(0, new BigDecimal("3000.00").compareTo(redisService.getBalance(accountId)),
                "Existing valid Redis balance should remain unchanged");
    }

    @Test
    void shouldNotOverwriteExistingMismatchRedisProjection() {
        Account account = createTestAccount(new BigDecimal("4000.00"));
        Long accountId = account.getAccountId();

        // Pre-populate mismatched Redis projection (e.g. 1500.00)
        redisService.setBalance(accountId, new BigDecimal("1500.00"));

        // Execute backfill
        boolean result = backfillService.backfillAccount(accountId);
        assertFalse(result, "backfillAccount should return false for mismatched existing projection");

        // Verify mismatched Redis balance was preserved for future detection
        assertEquals(0, new BigDecimal("1500.00").compareTo(redisService.getBalance(accountId)),
                "Existing mismatched Redis balance must NOT be overwritten");
    }

    @Test
    void shouldHandleNonExistentPostgreSQLAccountWithoutAlteringRedis() {
        Long nonExistentAccountId = System.currentTimeMillis() + 888888L;
        while (accountRepository.existsById(nonExistentAccountId)) {
            nonExistentAccountId++;
        }

        // Case A: No Redis key exists for non-existent account
        boolean resultA = backfillService.backfillAccount(nonExistentAccountId);
        assertFalse(resultA, "backfillAccount should return false for non-existent account");
        assertNull(redisService.getBalance(nonExistentAccountId), "No Redis projection should be created");

        // Case B: A Redis key happens to exist for non-existent account (synthetic orphan)
        redisService.setBalance(nonExistentAccountId, new BigDecimal("999.00"));
        createdRedisOnlyAccountIds.add(nonExistentAccountId);

        boolean resultB = backfillService.backfillAccount(nonExistentAccountId);
        assertFalse(resultB, "backfillAccount should return false for non-existent account even if orphan Redis key exists");
        assertEquals(0, new BigDecimal("999.00").compareTo(redisService.getBalance(nonExistentAccountId)),
                "Backfill must NOT delete or alter orphaned Redis keys (orphan cleanup is separate)");
    }

    @Test
    void shouldBackfillMultipleMissingAccounts() {
        Account acc1 = createTestAccount(new BigDecimal("100.00"));
        Account acc2 = createTestAccount(new BigDecimal("200.00"));
        Account acc3 = createTestAccount(new BigDecimal("300.00"));

        // Ensure all 3 projections are missing from Redis
        redisService.deleteBalance(acc1.getAccountId());
        redisService.deleteBalance(acc2.getAccountId());
        redisService.deleteBalance(acc3.getAccountId());

        List<Long> backfilledIds = backfillService.backfillAccounts(
                List.of(acc1.getAccountId(), acc2.getAccountId(), acc3.getAccountId())
        );

        assertEquals(3, backfilledIds.size(), "All 3 missing accounts should be backfilled");
        assertTrue(backfilledIds.contains(acc1.getAccountId()));
        assertTrue(backfilledIds.contains(acc2.getAccountId()));
        assertTrue(backfilledIds.contains(acc3.getAccountId()));

        assertEquals(0, new BigDecimal("100.00").compareTo(redisService.getBalance(acc1.getAccountId())));
        assertEquals(0, new BigDecimal("200.00").compareTo(redisService.getBalance(acc2.getAccountId())));
        assertEquals(0, new BigDecimal("300.00").compareTo(redisService.getBalance(acc3.getAccountId())));
    }

    @Test
    void shouldBeSafeAndIdempotentWhenRunRepeatedly() {
        Account acc = createTestAccount(new BigDecimal("500.00"));
        Long accountId = acc.getAccountId();
        redisService.deleteBalance(accountId);

        // Run 1
        boolean firstRun = backfillService.backfillAccount(accountId);
        assertTrue(firstRun, "First backfill run should succeed");
        assertEquals(0, new BigDecimal("500.00").compareTo(redisService.getBalance(accountId)));

        // Run 2 (repeated)
        boolean secondRun = backfillService.backfillAccount(accountId);
        assertFalse(secondRun, "Second backfill run should skip already projected account");
        assertEquals(0, new BigDecimal("500.00").compareTo(redisService.getBalance(accountId)));
    }

    @Test
    void shouldPreservePostgreSQLAccountBalances() {
        Account acc = createTestAccount(new BigDecimal("8888.88"));
        Long accountId = acc.getAccountId();
        redisService.deleteBalance(accountId);

        BigDecimal balanceBefore = accountRepository.findById(accountId).orElseThrow().getBalance();

        backfillService.backfillAccount(accountId);

        BigDecimal balanceAfter = accountRepository.findById(accountId).orElseThrow().getBalance();

        assertEquals(0, balanceBefore.compareTo(balanceAfter),
                "PostgreSQL balance must be strictly identical before and after backfill");
    }

    @Test
    void shouldTransitionReconciliationStatusFromMissingToMatch() {
        Account acc = createTestAccount(new BigDecimal("1234.56"));
        Long accountId = acc.getAccountId();
        redisService.deleteBalance(accountId);

        // 1. Verify reconciliation status before backfill is MISSING_FROM_REDIS
        ReconciliationResult reconBefore = reconciliationService.reconcileAccount(accountId);
        assertEquals(ReconciliationStatus.MISSING_FROM_REDIS, reconBefore.status(),
                "Status before backfill should be MISSING_FROM_REDIS");

        // 2. Perform backfill
        boolean backfilled = backfillService.backfillAccount(accountId);
        assertTrue(backfilled, "Backfill should succeed");

        // 3. Verify reconciliation status after backfill is MATCH
        ReconciliationResult reconAfter = reconciliationService.reconcileAccount(accountId);
        assertEquals(ReconciliationStatus.MATCH, reconAfter.status(),
                "Status after backfill should transition to MATCH");
        assertEquals(0, acc.getBalance().compareTo(reconAfter.redisBalance()),
                "Redis balance should match PostgreSQL balance in reconciliation result");
    }
}
