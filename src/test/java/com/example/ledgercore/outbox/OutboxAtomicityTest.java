package com.example.ledgercore.outbox;

import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.*;
import com.example.ledgercore.repository.*;
import com.example.ledgercore.service.DepositService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for verifying transactional atomicity between
 * financial operations and the transactional outbox in LedgerCore.
 *
 * <p>
 * This test verifies that when a failure occurs after a financial
 * transaction and its outbox event have been created, the complete
 * database transaction is rolled back.
 * </p>
 *
 * <p>
 * The test verifies that the transaction, ledger entries, account
 * balance changes, and outbox event are all rolled back together.
 * </p>
 *
 * <p>
 * This demonstrates the core guarantee of the transactional outbox
 * pattern: the financial state and its durable event record are
 * committed or rolled back as one database transaction.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class OutboxAtomicityTest {

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
    private OutboxRepository outboxRepository;

    @Autowired
    private DepositService depositService;

    @Autowired
    private PlatformTransactionManager transactionManager;


    @Test
    void shouldRollbackFinancialTransactionAndOutboxEventTogether() {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer savedCustomer = user.getCustomer();


        // 2. CREATE CUSTOMER ACCOUNT
        Account customerAccount = new Account();

        customerAccount.setAccountNumber(
                "ATOMICITY-" + System.currentTimeMillis()
        );
        customerAccount.setBalance(
                new BigDecimal("10000.00")
        );
        customerAccount.setCurrency(Currency.INR);
        customerAccount.setStatus(AccountStatus.ACTIVE);
        customerAccount.setAccountType(AccountType.CUSTOMER);
        customerAccount.setCustomer(savedCustomer);

        Account savedCustomerAccount =
                accountRepository.saveAndFlush(customerAccount);


        // 3. STORE INITIAL CUSTOMER BALANCE
        BigDecimal initialCustomerBalance =
                savedCustomerAccount.getBalance();


        // 4. LOAD SYSTEM ACCOUNT
        Account systemAccount =
                accountRepository.findByAccountNumber(
                        "LC-SYSTEM-INR"
                ).orElseThrow();


        // 5. STORE INITIAL SYSTEM BALANCE
        BigDecimal initialSystemBalance =
                systemAccount.getBalance();


        // 6. AUTHENTICATE USER
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );


        // 7. CREATE DEPOSIT REQUEST
        DepositRequest request = new DepositRequest();

        request.setAccountId(
                savedCustomerAccount.getAccountId()
        );

        request.setAmount(
                new BigDecimal("1000.00")
        );

        request.setCurrency(
                Currency.INR
        );

        request.setReference(
                "Outbox Atomicity Test"
        );


        // 8. SET UNIQUE IDEMPOTENCY KEY
        request.setIdempotencyKey(
                "ATOMICITY-"
                        + UUID.randomUUID()
        );


        /*
         * 9. CREATE A REAL SPRING DATABASE TRANSACTION
         *
         * The deposit service will participate in this transaction.
         */
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);


        final TransactionResponse[] responseHolder =
                new TransactionResponse[1];


        /*
         * 10. EXECUTE DEPOSIT INSIDE THE TRANSACTION
         *
         * The deposit creates:
         *
         * Transaction
         * Ledger entries
         * Balance updates
         * Outbox event
         *
         * All of these operations occur inside this transaction.
         */
        RuntimeException thrownException =
                assertThrows(
                        RuntimeException.class,
                        () -> transactionTemplate.execute(status -> {

                            // Execute the deposit
                            responseHolder[0] =
                                    depositService.deposit(request);


                            /*
                             * 11. DELIBERATELY FAIL AFTER DEPOSIT
                             *
                             * At this point the transaction and OutboxEvent
                             * have already been created.
                             *
                             * Throwing an exception causes the entire
                             * transaction to roll back.
                             */
                            throw new RuntimeException(
                                    "Intentional failure for atomicity test"
                            );
                        })
                );


        // 12. VERIFY THE EXPECTED FAILURE OCCURRED
        assertEquals(
                "Intentional failure for atomicity test",
                thrownException.getMessage()
        );


        // 13. VERIFY A TRANSACTION ID WAS CREATED BEFORE ROLLBACK
        assertNotNull(
                responseHolder[0]
        );

        Long transactionId =
                responseHolder[0].transactionId();


        /*
         * 14. RELOAD CUSTOMER ACCOUNT AFTER ROLLBACK
         *
         * The balance must be restored to its original value.
         */
        Account restoredCustomerAccount =
                accountRepository.findById(
                        savedCustomerAccount.getAccountId()
                ).orElseThrow();


        assertEquals(
                0,
                restoredCustomerAccount.getBalance()
                        .compareTo(initialCustomerBalance)
        );


        /*
         * 15. RELOAD SYSTEM ACCOUNT AFTER ROLLBACK
         *
         * The SYSTEM account must also be restored.
         */
        Account restoredSystemAccount =
                accountRepository.findByAccountNumber(
                        "LC-SYSTEM-INR"
                ).orElseThrow();


        assertEquals(
                0,
                restoredSystemAccount.getBalance()
                        .compareTo(initialSystemBalance)
        );


        /*
         * 16. VERIFY FINANCIAL TRANSACTION WAS ROLLED BACK
         */
        assertTrue(
                transactionRepository.findById(
                        transactionId
                ).isEmpty()
        );


        /*
         * 17. VERIFY LEDGER ENTRIES WERE ROLLED BACK
         */
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(
                                transactionId
                        );


        assertEquals(
                0,
                ledgerEntries.size()
        );


        /*
         * 18. VERIFY OUTBOX EVENT WAS ROLLED BACK
         *
         * This is the most important assertion in the test.
         *
         * The financial transaction was rolled back, therefore
         * its corresponding outbox event must also be gone.
         */
        List<OutboxEvent> outboxEvents =
                outboxRepository.findByAggregateId(
                        transactionId
                );


        assertEquals(
                0,
                outboxEvents.size()
        );


        /*
         * 19. VERIFY NO OUTBOX EVENT EXISTS FOR THE
         * ROLLED-BACK TRANSACTION.
         */
        assertTrue(
                outboxRepository.findByAggregateId(
                        transactionId
                ).isEmpty()
        );


        // 20. CLEANUP CUSTOMER ACCOUNT
        accountRepository.delete(
                restoredCustomerAccount
        );


        // 21. CLEAR SECURITY CONTEXT
        SecurityContextHolder.clearContext();
    }
}