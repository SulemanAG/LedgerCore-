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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for PostgreSQL and Redis balance reconciliation.
 *
 * <p>
 * PostgreSQL is treated as the source of truth while Redis is treated
 * as the read-side balance projection.
 * </p>
 *
 * <p>
 * These tests verify that the reconciliation service correctly detects:
 * matching balances, mismatched balances, and missing Redis projections.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class ReconciliationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountBalanceRedisService redisService;

    @Autowired
    private ReconciliationService reconciliationService;

    private Customer testCustomer;

    private Account testAccount;


    /**
     * Creates a valid customer and account before every test.
     */
    @BeforeEach
    void setUp() {

        // 1. Create a dedicated test customer.
        testCustomer = new Customer();

        testCustomer.setCustomerName(
                "Reconciliation Test Customer"
        );

        testCustomer.setCustomerAddress(
                "Test Address"
        );

        testCustomer.setCustomerPhoneNumber(
                "9999999999"
        );

        testCustomer.setCustomerEmail(
                "reconciliation."
                        + System.nanoTime()
                        + "@test.com"
        );

        // 2. Save the customer to PostgreSQL.
        testCustomer =
                customerRepository.saveAndFlush(
                        testCustomer
                );

        // 3. Create a valid test account.
        testAccount = new Account();

        testAccount.setAccountNumber(
                "999999999999"
                        + System.nanoTime()
        );

        testAccount.setBalance(
                new BigDecimal("5000.00")
        );

        testAccount.setCurrency(
                Currency.INR
        );

        testAccount.setStatus(
                AccountStatus.ACTIVE
        );

        testAccount.setAccountType(
                AccountType.CUSTOMER
        );

        testAccount.setCustomer(
                testCustomer
        );

        // 4. Save the account to PostgreSQL.
        testAccount =
                accountRepository.saveAndFlush(
                        testAccount
                );
    }


    /**
     * Removes the Redis projection, account and customer
     * after every test.
     */
    @AfterEach
    void tearDown() {

        // 1. Remove the Redis projection.
        if (testAccount != null
                && testAccount.getAccountId() != null) {

            redisService.deleteBalance(
                    testAccount.getAccountId()
            );
        }

        // 2. Remove the PostgreSQL account.
        if (testAccount != null
                && testAccount.getAccountId() != null) {

            accountRepository.deleteById(
                    testAccount.getAccountId()
            );
        }

        // 3. Remove the PostgreSQL customer.
        if (testCustomer != null
                && testCustomer.getCustomerId() != null) {

            customerRepository.deleteById(
                    testCustomer.getCustomerId()
            );
        }
    }


    /**
     * Verifies that matching PostgreSQL and Redis balances
     * produce a MATCH result.
     */
    @Test
    void shouldReturnMatchWhenBalancesAreEqual() {

        // 1. Store the same monetary value in Redis.
        redisService.setBalance(
                testAccount.getAccountId(),
                new BigDecimal("5000.00")
        );

        // 2. Reconcile the account.
        ReconciliationResult result =
                reconciliationService.reconcileAccount(
                        testAccount.getAccountId()
                );

        // 3. Verify the reconciliation status.
        assertEquals(
                ReconciliationStatus.MATCH,
                result.status()
        );

        // 4. Verify the PostgreSQL balance.
        //
        // compareTo() is used because PostgreSQL stores
        // the balance using scale 4, resulting in 5000.0000.
        assertEquals(
                0,
                result.databaseBalance().compareTo(
                        new BigDecimal("5000.00")
                )
        );

        // 5. Verify the Redis balance.
        assertEquals(
                0,
                result.redisBalance().compareTo(
                        new BigDecimal("5000.00")
                )
        );
    }


    /**
     * Verifies that different PostgreSQL and Redis balances
     * produce a MISMATCH result.
     */
    @Test
    void shouldReturnMismatchWhenBalancesAreDifferent() {

        // 1. Store an intentionally incorrect balance in Redis.
        redisService.setBalance(
                testAccount.getAccountId(),
                new BigDecimal("4500.00")
        );

        // 2. Reconcile the account.
        ReconciliationResult result =
                reconciliationService.reconcileAccount(
                        testAccount.getAccountId()
                );

        // 3. Verify that the discrepancy was detected.
        assertEquals(
                ReconciliationStatus.MISMATCH,
                result.status()
        );

        // 4. Verify the PostgreSQL balance.
        assertEquals(
                0,
                result.databaseBalance().compareTo(
                        new BigDecimal("5000.00")
                )
        );

        // 5. Verify the Redis balance.
        assertEquals(
                0,
                result.redisBalance().compareTo(
                        new BigDecimal("4500.00")
                )
        );
    }


    /**
     * Verifies that an account missing from Redis is reported
     * as MISSING_FROM_REDIS.
     */
    @Test
    void shouldReturnMissingWhenRedisProjectionDoesNotExist() {

        // 1. Ensure that the Redis projection does not exist.
        redisService.deleteBalance(
                testAccount.getAccountId()
        );

        // 2. Reconcile the account.
        ReconciliationResult result =
                reconciliationService.reconcileAccount(
                        testAccount.getAccountId()
                );

        // 3. Verify the reconciliation status.
        assertEquals(
                ReconciliationStatus.MISSING_FROM_REDIS,
                result.status()
        );

        // 4. Verify the PostgreSQL balance.
        assertEquals(
                0,
                result.databaseBalance().compareTo(
                        new BigDecimal("5000.00")
                )
        );

        // 5. Redis should contain no balance.
        assertNull(
                result.redisBalance()
        );
    }
}