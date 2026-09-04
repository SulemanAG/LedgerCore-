package com.example.ledgercore.transaction;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.model.Transaction;
import com.example.ledgercore.model.TransactionStatus;
import com.example.ledgercore.model.User;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.repository.UserRepository;
import com.example.ledgercore.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for verifying concurrent transfer idempotency in LedgerCore.
 *
 * <p>
 *     This test verifies that two concurrent requests using the same
 *     idempotency key cannot create duplicate financial transactions.
 * </p>
 *
 * <p>
 *     Both requests attempt to execute the same transfer simultaneously.
 *     Only one financial transaction should actually be processed, while
 *     the other request should return the already existing transaction.
 * </p>
 *
 * <p>
 *     The test also verifies that the account balances are updated only once,
 *     exactly one transaction exists, exactly two ledger entries are created,
 *     and the transaction is completed successfully.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class ConcurrentIdempotencyTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionService transactionService;


    /**
     * Verifies that two concurrent requests using the same idempotency key
     * cannot create duplicate financial transactions.
     *
     * <p>
     *     Both requests attempt to execute the same transfer simultaneously.
     *     The test verifies that the financial operation is processed only once.
     * </p>
     */
    @Test
    void shouldProcessConcurrentSameTransferOnlyOnce() throws Exception {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer customer = user.getCustomer();


        // 2. CREATE SOURCE ACCOUNT
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "CIDEMP-SRC-" + System.currentTimeMillis()
        );
        sourceAccount.setBalance(new BigDecimal("10000.00"));
        sourceAccount.setCurrency(Currency.INR);
        sourceAccount.setStatus(AccountStatus.ACTIVE);
        sourceAccount.setAccountType(AccountType.CUSTOMER);
        sourceAccount.setCustomer(customer);

        Account savedSourceAccount =
                accountRepository.save(sourceAccount);


        // 3. CREATE DESTINATION ACCOUNT
        Account destinationAccount = new Account();

        destinationAccount.setAccountNumber(
                "CIDEMP-DEST-" + System.currentTimeMillis()
        );
        destinationAccount.setBalance(new BigDecimal("5000.00"));
        destinationAccount.setCurrency(Currency.INR);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
        destinationAccount.setAccountType(AccountType.CUSTOMER);
        destinationAccount.setCustomer(customer);

        Account savedDestinationAccount =
                accountRepository.save(destinationAccount);


        // 4. CREATE TRANSFER REQUEST
        TransferRequest request = new TransferRequest();

        request.setSourceAccountId(
                savedSourceAccount.getAccountId()
        );

        request.setDestinationAccountId(
                savedDestinationAccount.getAccountId()
        );

        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency(Currency.INR);
        request.setReference("Concurrent Idempotency Test");


        // 5. SET SAME IDEMPOTENCY KEY FOR BOTH REQUESTS
        request.setIdempotencyKey(
                "CONCURRENT-IDEMP-" + UUID.randomUUID()
        );


        // 6. CREATE THREAD POOL
        ExecutorService executorService =
                Executors.newFixedThreadPool(2);


        // 7. CREATE START LATCH
        CountDownLatch startLatch =
                new CountDownLatch(1);


        // 8. CREATE SECURITY CONTEXT FOR USER A
        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );


        // 9. CREATE FIRST CONCURRENT REQUEST
        Future<TransactionResponse> firstFuture =
                executorService.submit(() -> {

                    SecurityContextHolder.setContext(securityContext);

                    startLatch.await();

                    try {
                        return transactionService.transfer(request);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });


        // 10. CREATE SECOND CONCURRENT REQUEST
        Future<TransactionResponse> secondFuture =
                executorService.submit(() -> {

                    SecurityContextHolder.setContext(securityContext);

                    startLatch.await();

                    try {
                        return transactionService.transfer(request);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });


        // 11. RELEASE BOTH THREADS AT THE SAME TIME
        startLatch.countDown();


        // 12. WAIT FOR FIRST REQUEST
        TransactionResponse firstResponse = null;
        Exception firstException = null;

        try {
            firstResponse = firstFuture.get();
        } catch (Exception e) {
            firstException = e;
        }


        // 13. WAIT FOR SECOND REQUEST
        TransactionResponse secondResponse = null;
        Exception secondException = null;

        try {
            secondResponse = secondFuture.get();
        } catch (Exception e) {
            secondException = e;
        }


        // 14. PRINT ANY CONCURRENT REQUEST EXCEPTIONS
        if (firstException != null) {
            System.out.println("FIRST REQUEST FAILED:");
            firstException.printStackTrace();
        }

        if (secondException != null) {
            System.out.println("SECOND REQUEST FAILED:");
            secondException.printStackTrace();
        }


        // 15. VERIFY BOTH REQUESTS COMPLETED SUCCESSFULLY
        assertNull(
                firstException,
                "First concurrent request failed"
        );

        assertNull(
                secondException,
                "Second concurrent request failed"
        );


        // 16. SHUT DOWN THREAD POOL
        executorService.shutdown();


        // 17. VERIFY BOTH REQUESTS RETURN SAME TRANSACTION
        assertEquals(
                firstResponse.transactionId(),
                secondResponse.transactionId()
        );


        // 18. VERIFY SOURCE ACCOUNT BALANCE
        Account updatedSourceAccount =
                accountRepository.findById(
                        savedSourceAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedSourceAccount.getBalance()
                        .compareTo(new BigDecimal("9000.00"))
        );


        // 19. VERIFY DESTINATION ACCOUNT BALANCE
        Account updatedDestinationAccount =
                accountRepository.findById(
                        savedDestinationAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedDestinationAccount.getBalance()
                        .compareTo(new BigDecimal("6000.00"))
        );


        // 20. VERIFY ONLY ONE TRANSACTION EXISTS
        List<Transaction> transactions =
                transactionRepository
                        .findDistinctByLedgerEntriesAccountAccountId(
                                savedSourceAccount.getAccountId()
                        );

        assertEquals(
                1,
                transactions.size()
        );


        // 21. VERIFY EXACTLY TWO LEDGER ENTRIES EXIST
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(
                                firstResponse.transactionId()
                        );

        assertEquals(
                2,
                ledgerEntries.size()
        );


        // 22. VERIFY TRANSACTION IS COMPLETED
        assertEquals(
                TransactionStatus.COMPLETED,
                firstResponse.status()
        );


        // 23. CLEANUP LEDGER ENTRIES
        ledgerEntryRepository.deleteAll(ledgerEntries);


        // 24. CLEANUP TRANSACTION
        transactionRepository.deleteById(
                firstResponse.transactionId()
        );


        // 25. RELOAD ACCOUNTS FOR CLEANUP
        Account sourceAccountForCleanup =
                accountRepository.findById(
                        savedSourceAccount.getAccountId()
                ).orElseThrow();

        Account destinationAccountForCleanup =
                accountRepository.findById(
                        savedDestinationAccount.getAccountId()
                ).orElseThrow();


        // 26. CLEANUP ACCOUNTS
        accountRepository.delete(sourceAccountForCleanup);
        accountRepository.delete(destinationAccountForCleanup);
    }
}