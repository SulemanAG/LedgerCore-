package com.example.ledgercore.transaction;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;

/**
 * Integration tests for transaction atomicity in LedgerCore.
 *
 * <p>These tests verify that a financial transfer behaves as an
 * all-or-nothing operation. If a transfer fails after database changes
 * have started, all changes made within the transaction must be rolled back.</p>
 *
 * <p>The tests verify that account balances, transaction records, and
 * ledger entries remain consistent when a transaction fails.</p>
 *
 * <p>This class uses the real Spring application context and PostgreSQL
 * database so that Spring's {@code @Transactional} behavior can be tested.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class TransactionAtomicityTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionService transactionService;

    /**
     * Spy the real ledger repository.
     *
     * <p>The real repository is still used, but we can deliberately
     * make one database operation fail during the test.</p>
     */
    @MockitoSpyBean
    private LedgerEntryRepository ledgerEntryRepositorySpy;


    /**
     * Verifies that a failed transfer is completely rolled back.
     *
     * <p>The test creates a source and destination account, records their
     * initial balances and database state, and then forces the second
     * ledger entry operation to fail.</p>
     *
     * <p>The transfer has already created the transaction record and the
     * first ledger entry at this point. Therefore, if transaction management
     * is working correctly, those changes must also be rolled back.</p>
     */
    @Test
    void shouldRollbackFailedTransfer() {


        // 1. CREATE TEST CUSTOMER
        Customer customer = customerRepository.findById(1L)
                .orElseThrow();


        // 2. CREATE SOURCE ACCOUNT
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "ATOMIC-SOURCE-" + System.currentTimeMillis()
        );

        sourceAccount.setBalance(
                new BigDecimal("10000.00")
        );

        sourceAccount.setCurrency(Currency.INR);
        sourceAccount.setStatus(AccountStatus.ACTIVE);
        sourceAccount.setAccountType(AccountType.CUSTOMER);
        sourceAccount.setCustomer(customer);

        sourceAccount = accountRepository.save(sourceAccount);


        // 3. CREATE DESTINATION ACCOUNT
        Account destinationAccount = new Account();

        destinationAccount.setAccountNumber(
                "ATOMIC-DEST-" + System.currentTimeMillis()
        );

        destinationAccount.setBalance(
                new BigDecimal("5000.00")
        );

        destinationAccount.setCurrency(Currency.INR);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
        destinationAccount.setAccountType(AccountType.CUSTOMER);
        destinationAccount.setCustomer(customer);

        destinationAccount = accountRepository.save(destinationAccount);

        Long sourceAccountId = sourceAccount.getAccountId();
        Long destinationAccountId = destinationAccount.getAccountId();


        // 4. RECORD INITIAL DATABASE STATE
        BigDecimal initialSourceBalance =
                sourceAccount.getBalance();

        BigDecimal initialDestinationBalance =
                destinationAccount.getBalance();

        long initialTransactionCount =
                transactionRepository.count();

        long initialLedgerEntryCount =
                ledgerEntryRepository.count();


        // 5. PREPARE AUTHENTICATION
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );

        SecurityContextHolder.setContext(context);

        // 6. FORCE FAILURE DURING LEDGER CREATION
        AtomicInteger ledgerSaveCount =
                new AtomicInteger(0);

        doAnswer(invocation -> {

            int currentCount =
                    ledgerSaveCount.incrementAndGet();

            /*
             * The transfer creates:
             *
             * 1. DEBIT ledger entry
             * 2. CREDIT ledger entry
             *
             * Allow the first one to be saved.
             *
             * Force the second one to fail.
             */
            if (currentCount == 2) {
                throw new RuntimeException(
                        "Simulated ledger failure"
                );
            }

            return invocation.callRealMethod();

        }).when(ledgerEntryRepositorySpy).save(
                org.mockito.ArgumentMatchers.any()
        );

        try {


            // 7. CREATE TRANSFER REQUEST
            TransferRequest request =
                    new TransferRequest();

            request.setSourceAccountId(sourceAccountId);
            request.setDestinationAccountId(destinationAccountId);
            request.setAmount(new BigDecimal("1000.00"));
            request.setCurrency(Currency.INR);
            request.setReference("Atomicity test");


            // 8. EXECUTE TRANSFER AND EXPECT FAILURE
            assertThrows(
                    RuntimeException.class,
                    () -> transactionService.transfer(request)
            );


            // 9. VERIFY ACCOUNT BALANCES WERE ROLLED BACK
            Account sourceAfterRollback =
                    accountRepository.findById(sourceAccountId)
                            .orElseThrow();

            Account destinationAfterRollback =
                    accountRepository.findById(destinationAccountId)
                            .orElseThrow();

            assertEquals(
                    0,
                    initialSourceBalance.compareTo(
                            sourceAfterRollback.getBalance()
                    ),
                    "Source balance should be unchanged after rollback"
            );

            assertEquals(
                    0,
                    initialDestinationBalance.compareTo(
                            destinationAfterRollback.getBalance()
                    ),
                    "Destination balance should be unchanged after rollback"
            );


            // 10. VERIFY TRANSACTION RECORD WAS ROLLED BACK
            assertEquals(
                    initialTransactionCount,
                    transactionRepository.count(),
                    "Transaction record should be rolled back"
            );


            // 11. VERIFY LEDGER ENTRIES WERE ROLLED BACK
            assertEquals(
                    initialLedgerEntryCount,
                    ledgerEntryRepository.count(),
                    "Ledger entries should be rolled back"
            );

        } finally {


            // 12. CLEAR AUTHENTICATION
            SecurityContextHolder.clearContext();


            // 13. CLEAN UP TEST ACCOUNTS
            accountRepository.deleteById(sourceAccountId);
            accountRepository.deleteById(destinationAccountId);
        }
    }
}