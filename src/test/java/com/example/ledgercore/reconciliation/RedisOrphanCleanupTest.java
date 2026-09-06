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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit and test suite for cleaning historical orphaned Redis account balance projections.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class RedisOrphanCleanupTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountBalanceRedisService redisService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private RedisOrphanCleanupService orphanCleanupService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Customer testCustomer;
    private Account validTestAccount;
    private Long syntheticOrphanAccountId;

    @BeforeEach
    void setUp() {
        // Create a valid customer & account in PostgreSQL and set projection in Redis
        testCustomer = new Customer();
        testCustomer.setCustomerName("Orphan Audit Test Customer");
        testCustomer.setCustomerAddress("123 Test St");
        testCustomer.setCustomerPhoneNumber("8888888888");
        testCustomer.setCustomerEmail("orphan.audit." + System.nanoTime() + "@test.com");
        testCustomer = customerRepository.saveAndFlush(testCustomer);

        validTestAccount = new Account();
        validTestAccount.setAccountNumber("AUDIT" + System.nanoTime());
        validTestAccount.setBalance(new BigDecimal("1000.00"));
        validTestAccount.setCurrency(Currency.INR);
        validTestAccount.setStatus(AccountStatus.ACTIVE);
        validTestAccount.setAccountType(AccountType.CUSTOMER);
        validTestAccount.setCustomer(testCustomer);
        validTestAccount = accountRepository.saveAndFlush(validTestAccount);

        redisService.setBalance(validTestAccount.getAccountId(), new BigDecimal("1000.00"));

        // Create a synthetic orphaned projection in Redis (no DB account)
        syntheticOrphanAccountId = System.currentTimeMillis() + 999999L;
        // Ensure this ID does not exist in DB
        while (accountRepository.existsById(syntheticOrphanAccountId)) {
            syntheticOrphanAccountId++;
        }
        redisService.setBalance(syntheticOrphanAccountId, new BigDecimal("777.00"));
    }

    @AfterEach
    void tearDown() {
        if (validTestAccount != null && validTestAccount.getAccountId() != null) {
            redisService.deleteBalance(validTestAccount.getAccountId());
            accountRepository.deleteById(validTestAccount.getAccountId());
        }
        if (testCustomer != null && testCustomer.getCustomerId() != null) {
            customerRepository.deleteById(testCustomer.getCustomerId());
        }
        if (syntheticOrphanAccountId != null) {
            redisService.deleteBalance(syntheticOrphanAccountId);
        }
    }

    @Test
    void testAuditAndCleanupServiceLogic() {
        Set<Long> validAccountIds = orphanCleanupService.findValidAccountIds();
        Set<Long> orphanedAccountIds = orphanCleanupService.findOrphanedAccountIds();

        assertTrue(validAccountIds.contains(validTestAccount.getAccountId()),
                "Valid account ID should be present in valid set");
        assertTrue(orphanedAccountIds.contains(syntheticOrphanAccountId),
                "Synthetic orphan account ID should be present in orphan set");
        assertFalse(validAccountIds.contains(syntheticOrphanAccountId),
                "Synthetic orphan account ID should NOT be in valid set");

        // Perform cleanup
        List<Long> cleanedIds = orphanCleanupService.cleanOrphanedProjections();
        assertTrue(cleanedIds.contains(syntheticOrphanAccountId),
                "Cleaned IDs should contain synthetic orphan account ID");

        // Verify Redis state after cleanup
        assertNull(redisService.getBalance(syntheticOrphanAccountId),
                "Synthetic orphan balance projection should be deleted from Redis");
        assertEquals(0, new BigDecimal("1000.00").compareTo(redisService.getBalance(validTestAccount.getAccountId())),
                "Valid account balance projection should remain intact in Redis");
    }

    @Test
    void performFullAuditAndCleanHistoricalOrphans() {
        // Step 1: Discover all raw Redis keys matching pattern
        Set<String> rawBalanceKeys = redisTemplate.keys("ledgercore:account:*:balance");
        if (rawBalanceKeys == null) {
            rawBalanceKeys = new HashSet<>();
        }

        Set<String> allAccountKeys = redisTemplate.keys("ledgercore:account:*");
        if (allAccountKeys == null) {
            allAccountKeys = new HashSet<>();
        }

        Set<Long> projectedIds = redisService.findProjectedAccountIds();
        Set<Long> confirmedOrphansBefore = orphanCleanupService.findOrphanedAccountIds();
        Set<Long> validProjectionsBefore = orphanCleanupService.findValidAccountIds();

        List<ReconciliationResult> reconBefore = reconciliationService.reconcileAllAccounts();

        long orphanedCountBefore = reconBefore.stream()
                .filter(r -> r.status() == ReconciliationStatus.ORPHANED_IN_REDIS)
                .count();

        // Print audit results to stdout for inspection
        System.out.println("=================================================");
        System.out.println("HISTORICAL REDIS PROJECTION AUDIT REPORT BEFORE CLEANUP");
        System.out.println("=================================================");
        System.out.println("Total raw Redis balance keys discovered: " + rawBalanceKeys.size());
        System.out.println("Total raw Redis account keys discovered: " + allAccountKeys.size());
        System.out.println("Parsed projected account IDs count: " + projectedIds.size());
        System.out.println("Valid projections count (PostgreSQL exists): " + validProjectionsBefore.size());
        System.out.println("Confirmed orphaned projections count (PostgreSQL absent): " + confirmedOrphansBefore.size());
        System.out.println("Reconciliation ORPHANED_IN_REDIS count: " + orphanedCountBefore);
        System.out.println("Confirmed orphaned Account IDs selected for deletion: " + confirmedOrphansBefore);
        System.out.println("=================================================");

        // Step 2: Clean confirmed orphans
        List<Long> deletedOrphanIds = orphanCleanupService.cleanOrphanedProjections();

        // Step 3: Post-cleanup verification
        Set<Long> confirmedOrphansAfter = orphanCleanupService.findOrphanedAccountIds();
        Set<Long> validProjectionsAfter = orphanCleanupService.findValidAccountIds();
        List<ReconciliationResult> reconAfter = reconciliationService.reconcileAllAccounts();

        long orphanedCountAfter = reconAfter.stream()
                .filter(r -> r.status() == ReconciliationStatus.ORPHANED_IN_REDIS)
                .count();

        System.out.println("=================================================");
        System.out.println("HISTORICAL REDIS PROJECTION AUDIT REPORT AFTER CLEANUP");
        System.out.println("=================================================");
        System.out.println("Exact number of Redis keys deleted: " + deletedOrphanIds.size());
        System.out.println("Confirmed orphaned projections count after: " + confirmedOrphansAfter.size());
        System.out.println("Valid projections count after: " + validProjectionsAfter.size());
        System.out.println("Reconciliation ORPHANED_IN_REDIS count after: " + orphanedCountAfter);
        System.out.println("=================================================");

        // Assertions: valid projections must remain untouched, orphans must be gone
        assertEquals(validProjectionsBefore.size(), validProjectionsAfter.size(),
                "Valid projections count must not decrease");
        assertEquals(0, confirmedOrphansAfter.size(),
                "All confirmed orphans should be cleaned");
    }
}
