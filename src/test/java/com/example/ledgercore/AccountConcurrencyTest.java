package com.example.ledgercore;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for account concurrency behavior.
 *
 * <p>These tests verify that LedgerCore can detect concurrent updates
 * to the same account using JPA optimistic locking.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class AccountConcurrencyTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountConcurrencyService accountConcurrencyService;

    @Autowired
    private TransactionService transactionService;

    /**
     * Verifies that the Spring Boot application context can load successfully.
     */
    @Test
    void contextLoads() {
    }

    /**
     * Verifies that the test can communicate with PostgreSQL
     * through Spring Data JPA.
     */
    @Test
    void shouldReadAccountFromDatabase() {

        Account account = accountRepository.findById(4L)
                .orElseThrow();

        assertNotNull(account);
    }

    /**
     * Attempts to update the same account concurrently from two
     * independent transactions.
     *
     * <p>The account uses {@code @Version}, so when both transactions
     * attempt to update the same version of the account, one transaction
     * should succeed while the other should encounter an optimistic
     * locking conflict.</p>
     */
    @Test
    void shouldDetectConcurrentAccountUpdates() throws Exception {

        /*
         * Use an existing customer because Account.customer is mandatory.
         */
        Customer customer = customerRepository.findById(1L)
                .orElseThrow();

        /*
         * Create a temporary account specifically for this test.
         */
        Account account = new Account();

        account.setAccountNumber(
                "TEST-" + System.currentTimeMillis()
        );

        account.setBalance(
                new BigDecimal("1000.00")
        );

        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.CUSTOMER);

        /*
         * Account.customer is mandatory.
         */
        account.setCustomer(customer);

        Account savedAccount =
                accountRepository.save(account);

        Long accountId =
                savedAccount.getAccountId();

        /*
         * CountDownLatch acts as a gate.
         *
         * Both worker threads wait here until the main test
         * thread releases them.
         */
        CountDownLatch startLatch =
                new CountDownLatch(1);

        /*
         * Create exactly two worker threads.
         */
        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        try {

            /*
             * Thread A attempts to change:
             *
             * 1000 → 1100
             */
            var futureA = executorService.submit(() -> {

                try {

                    /*
                     * Wait until Thread B is also ready.
                     */
                    startLatch.await();

                    accountConcurrencyService.updateAccountBalance(
                            accountId,
                            new BigDecimal("1100.00")
                    );

                    return "SUCCESS";

                } catch (Exception exception) {

                    return exception
                            .getClass()
                            .getSimpleName();
                }
            });

            /*
             * Thread B attempts to change:
             *
             * 1000 → 1200
             */
            var futureB = executorService.submit(() -> {

                try {

                    /*
                     * Wait until Thread A is also ready.
                     */
                    startLatch.await();

                    accountConcurrencyService.updateAccountBalance(
                            accountId,
                            new BigDecimal("1200.00")
                    );

                    return "SUCCESS";

                } catch (Exception exception) {

                    return exception
                            .getClass()
                            .getSimpleName();
                }
            });

            /*
             * Release both worker threads.
             */
            startLatch.countDown();

            /*
             * Wait for both operations to finish.
             */
            String resultA =
                    futureA.get(10, TimeUnit.SECONDS);

            String resultB =
                    futureB.get(10, TimeUnit.SECONDS);

            /*
             * Display the result so we can inspect what Hibernate
             * actually did before making the test strict.
             */
            System.out.println(
                    "Thread A result: " + resultA
            );

            System.out.println(
                    "Thread B result: " + resultB
            );

            assertEquals(
                    1,
                    java.util.stream.Stream.of(resultA, resultB)
                            .filter("SUCCESS"::equals)
                            .count(),
                    "Exactly one concurrent update should succeed"
            );

            assertEquals(
                    1,
                    java.util.stream.Stream.of(resultA, resultB)
                            .filter("ObjectOptimisticLockingFailureException"::equals)
                            .count(),
                    "Exactly one concurrent update should fail due to optimistic locking"
            );

        } finally {

            /*
             * Stop the worker threads.
             */
            executorService.shutdown();

            /*
             * Remove the temporary account created by this test.
             */
            accountRepository.deleteById(accountId);
        }
    }


}