package com.example.ledgercore.transaction;

import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.*;
import com.example.ledgercore.repository.*;
import com.example.ledgercore.service.DepositService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for verifying concurrent deposit idempotency in LedgerCore.
 *
 * <p>
 *     Verifies that when two concurrent threads submit identical deposit requests
 *     using the same idempotency key simultaneously, only one deposit operation is
 *     processed and both callers receive the same transaction result.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class ConcurrentDepositIdempotencyTest {

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
    private DepositService depositService;


    @Test
    void shouldProcessConcurrentSameDepositOnlyOnce() throws Exception {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer customer = user.getCustomer();

        // 2. CREATE CUSTOMER ACCOUNT
        Account account = new Account();
        account.setAccountNumber("CDEP-SRC-" + System.currentTimeMillis());
        account.setBalance(new BigDecimal("5000.00"));
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.CUSTOMER);
        account.setCustomer(customer);

        Account savedAccount = accountRepository.save(account);

        // 3. CREATE DEPOSIT REQUEST
        DepositRequest request = new DepositRequest();
        request.setAccountId(savedAccount.getAccountId());
        request.setAmount(new BigDecimal("2000.00"));
        request.setCurrency(Currency.INR);
        request.setReference("Concurrent Deposit Idempotency Test");

        // 4. SET SAME IDEMPOTENCY KEY FOR BOTH REQUESTS
        String idempotencyKey = "CONCURRENT-DEP-IDEMP-" + UUID.randomUUID();
        request.setIdempotencyKey(idempotencyKey);

        // 5. CREATE THREAD POOL
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // 6. CREATE START LATCH
        CountDownLatch startLatch = new CountDownLatch(1);

        // 7. CREATE SECURITY CONTEXT FOR USER A
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );

        // 8. SUBMIT FIRST CONCURRENT REQUEST
        Future<TransactionResponse> firstFuture = executorService.submit(() -> {
            SecurityContextHolder.setContext(securityContext);
            startLatch.await();
            try {
                return depositService.deposit(request);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        // 9. SUBMIT SECOND CONCURRENT REQUEST
        Future<TransactionResponse> secondFuture = executorService.submit(() -> {
            SecurityContextHolder.setContext(securityContext);
            startLatch.await();
            try {
                return depositService.deposit(request);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        // 10. RELEASE BOTH THREADS SIMULTANEOUSLY
        startLatch.countDown();

        // 11. WAIT FOR FIRST REQUEST
        TransactionResponse firstResponse = null;
        Exception firstException = null;
        try {
            firstResponse = firstFuture.get();
        } catch (Exception e) {
            firstException = e;
        }

        // 12. WAIT FOR SECOND REQUEST
        TransactionResponse secondResponse = null;
        Exception secondException = null;
        try {
            secondResponse = secondFuture.get();
        } catch (Exception e) {
            secondException = e;
        }

        // 13. PRINT ANY CONCURRENT REQUEST EXCEPTIONS FOR DIAGNOSTICS
        if (firstException != null) {
            System.out.println("FIRST DEPOSIT REQUEST FAILED:");
            firstException.printStackTrace();
        }

        if (secondException != null) {
            System.out.println("SECOND DEPOSIT REQUEST FAILED:");
            secondException.printStackTrace();
        }

        // 14. VERIFY BOTH REQUESTS SUCCEEDED
        assertNull(firstException, "First concurrent deposit request failed");
        assertNull(secondException, "Second concurrent deposit request failed");

        // 15. SHUT DOWN THREAD POOL
        executorService.shutdown();

        // 16. VERIFY BOTH REQUESTS RETURN SAME TRANSACTION
        assertEquals(firstResponse.transactionId(), secondResponse.transactionId());

        // 17. VERIFY ACCOUNT BALANCE CREDITED EXACTLY ONCE
        Account updatedAccount = accountRepository.findById(savedAccount.getAccountId()).orElseThrow();
        assertEquals(0, updatedAccount.getBalance().compareTo(new BigDecimal("7000.00")));

        // 18. VERIFY ONLY ONE TRANSACTION EXISTS
        List<Transaction> transactions = transactionRepository
                .findDistinctByLedgerEntriesAccountAccountId(savedAccount.getAccountId());
        assertEquals(1, transactions.size());

        // 19. VERIFY EXACTLY TWO LEDGER ENTRIES EXIST
        List<LedgerEntry> ledgerEntries = ledgerEntryRepository
                .findByTransactionTransactionId(firstResponse.transactionId());
        assertEquals(2, ledgerEntries.size());

        // 20. VERIFY TRANSACTION IS COMPLETED
        assertEquals(TransactionStatus.COMPLETED, firstResponse.status());

        // 21. CLEANUP LEDGER ENTRIES
        ledgerEntryRepository.deleteAll(ledgerEntries);

        // 22. CLEANUP TRANSACTION
        transactionRepository.deleteById(firstResponse.transactionId());

        // 23. RELOAD ACCOUNT BEFORE DELETION (OPTIMISTIC LOCKING SAFETY)
        Account accountForCleanup = accountRepository.findById(savedAccount.getAccountId()).orElseThrow();

        // 24. CLEANUP ACCOUNT
        accountRepository.delete(accountForCleanup);
    }
}
