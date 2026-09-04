package com.example.ledgercore.transaction;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TransferConcurrencyTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionService transactionService;


    /**
     * Tests concurrent transfers originating from the same source account.
     *
     * <p>Creates one source account with an initial balance and two
     * destination accounts that will later be used by concurrent transfers.</p>
     */
    @Test
    void shouldHandleConcurrentTransfersFromSameSource()
            throws Exception {


        // Find the customer who owns the test accounts
        Customer customer = customerRepository.findById(1L)
                .orElseThrow();



        // Create source account
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "TEST-SOURCE-" + System.currentTimeMillis()
        );

        sourceAccount.setBalance(
                new BigDecimal("10000.00")
        );

        sourceAccount.setCurrency(Currency.INR);
        sourceAccount.setStatus(AccountStatus.ACTIVE);
        sourceAccount.setAccountType(AccountType.CUSTOMER);
        sourceAccount.setCustomer(customer);




        // Create destination account 1
        Account destinationAccount1 = new Account();

        destinationAccount1.setAccountNumber(
                "TEST-DEST-1-" + System.currentTimeMillis()
        );

        destinationAccount1.setBalance(
                BigDecimal.ZERO
        );

        destinationAccount1.setCurrency(Currency.INR);
        destinationAccount1.setStatus(AccountStatus.ACTIVE);
        destinationAccount1.setAccountType(AccountType.CUSTOMER);
        destinationAccount1.setCustomer(customer);





        // Create destination account 2
        Account destinationAccount2 = new Account();

        destinationAccount2.setAccountNumber(
                "TEST-DEST-2-" + System.currentTimeMillis()
        );

        destinationAccount2.setBalance(
                BigDecimal.ZERO
        );

        destinationAccount2.setCurrency(Currency.INR);
        destinationAccount2.setStatus(AccountStatus.ACTIVE);
        destinationAccount2.setAccountType(AccountType.CUSTOMER);
        destinationAccount2.setCustomer(customer);




        // Persist all three accounts
        sourceAccount = accountRepository.save(sourceAccount);

        destinationAccount1 =
                accountRepository.save(destinationAccount1);

        destinationAccount2 =
                accountRepository.save(destinationAccount2);



        // Capture generated database IDs
        Long sourceAccountId =
                sourceAccount.getAccountId();

        Long destinationAccountId1 =
                destinationAccount1.getAccountId();

        Long destinationAccountId2 =
                destinationAccount2.getAccountId();


        //Create Transfer request 1
        // source - Destination 1
        TransferRequest transferRequest1 = new TransferRequest();
        transferRequest1.setSourceAccountId(sourceAccountId);
        transferRequest1.setDestinationAccountId(destinationAccountId1);
        transferRequest1.setAmount(new BigDecimal("1000.00"));
        transferRequest1.setCurrency(Currency.INR);
        transferRequest1.setReference("Concurrent transfer test-1");


        //Create transfer request 2
        //source - destination 2
        TransferRequest transferRequest2= new TransferRequest();
        transferRequest2.setSourceAccountId(sourceAccountId);
        transferRequest2.setDestinationAccountId(destinationAccountId2);
        transferRequest2.setAmount(new BigDecimal("1000.00"));
        transferRequest2.setCurrency(Currency.INR);
        transferRequest2.setReference("Concurrent transfer test-2");



        //Synchronization for concurrent Execution
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService= Executors.newFixedThreadPool(2);


        try{


            // Thread A
            // Source → Destination 1
            // ₹1,000
            var futureA = executorService.submit(() -> {

                try {

                    SecurityContext context =
                            SecurityContextHolder.createEmptyContext();

                    context.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "userA",
                                    null,
                                    java.util.List.of()
                            )
                    );

                    SecurityContextHolder.setContext(context);

                    startLatch.await();

                    transactionService.transfer(
                            transferRequest1
                    );

                    return "SUCCESS";

                } catch (Exception exception) {

                    exception.printStackTrace();

                    return exception
                            .getClass()
                            .getSimpleName();

                } finally {

                    SecurityContextHolder.clearContext();
                }
            });




            // Thread B
            // Source → Destination 2
            // ₹1,000
            var futureB = executorService.submit(() -> {

                try {

                    SecurityContext context =
                            SecurityContextHolder.createEmptyContext();

                    context.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "userA",
                                    null,
                                    java.util.List.of()
                            )
                    );

                    SecurityContextHolder.setContext(context);

                    startLatch.await();

                    transactionService.transfer(
                            transferRequest2
                    );

                    return "SUCCESS";

                } catch (Exception exception) {

                    exception.printStackTrace();

                    return exception
                            .getClass()
                            .getSimpleName();

                } finally {

                    SecurityContextHolder.clearContext();
                }
            });

            //Release both the threads
            startLatch.countDown();

            String resultA = futureA.get(10,TimeUnit.SECONDS);
            String resultB = futureB.get(10,TimeUnit.SECONDS);

            System.out.println("Transfer Thread A result: "+resultA);
            System.out.println("Transfer thread B result: "+resultB);

            assertEquals(
                    1,
                    java.util.stream.Stream.of(resultA, resultB)
                            .filter("SUCCESS"::equals)
                            .count(),
                    "Exactly one concurrent transfer should succeed"
            );

            assertEquals(
                    1,
                    java.util.stream.Stream.of(resultA, resultB)
                            .filter("ObjectOptimisticLockingFailureException"::equals)
                            .count(),
                    "Exactly one concurrent transfer should fail due to optimistic locking"
            );
        }
        finally {
            executorService.shutdown();
        }

    }
}