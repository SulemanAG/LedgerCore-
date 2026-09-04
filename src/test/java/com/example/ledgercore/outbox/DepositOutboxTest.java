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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for verifying transactional outbox behavior
 * for deposit operations in LedgerCore.
 *
 * <p>
 * This test verifies that a successfully completed deposit creates
 * exactly one durable outbox event associated with the financial
 * transaction.
 * </p>
 *
 * <p>
 * The test verifies the transaction, double-entry ledger entries,
 * account balances, outbox event type, status, aggregate ID,
 * and serialized event payload.
 * </p>
 *
 * <p>
 * This ensures that a successful deposit creates the durable event
 * record required by the future outbox relay and Kafka publishing
 * pipeline.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class DepositOutboxTest {

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


    @Test
    @Transactional
    void shouldCreateOutboxEventForSuccessfulDeposit() {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer savedCustomer = user.getCustomer();


        // 2. CREATE CUSTOMER ACCOUNT
        Account customerAccount = new Account();

        customerAccount.setAccountNumber(
                "OUTBOX-DEP-" + System.currentTimeMillis()
        );
        customerAccount.setBalance(new BigDecimal("5000.00"));
        customerAccount.setCurrency(Currency.INR);
        customerAccount.setStatus(AccountStatus.ACTIVE);
        customerAccount.setAccountType(AccountType.CUSTOMER);
        customerAccount.setCustomer(savedCustomer);

        Account savedCustomerAccount =
                accountRepository.save(customerAccount);


        // 3. LOAD SYSTEM ACCOUNT
        Account systemAccount =
                accountRepository.findByAccountNumber("LC-SYSTEM-INR")
                        .orElseThrow();


        // 4. STORE INITIAL SYSTEM ACCOUNT BALANCE
        BigDecimal initialSystemBalance =
                systemAccount.getBalance();


        // 5. AUTHENTICATE USER
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );


        // 6. CREATE DEPOSIT REQUEST
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
                "Deposit Outbox Test"
        );


        // 7. SET IDEMPOTENCY KEY
        request.setIdempotencyKey(
                "OUTBOX-DEPOSIT-"
                        + java.util.UUID.randomUUID()
        );


        // 8. EXECUTE DEPOSIT
        TransactionResponse response =
                depositService.deposit(request);


        // 9. VERIFY TRANSACTION IS COMPLETED
        assertEquals(
                TransactionStatus.COMPLETED,
                response.status()
        );


        // 10. VERIFY CUSTOMER ACCOUNT BALANCE
        Account updatedCustomerAccount =
                accountRepository.findById(
                        savedCustomerAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedCustomerAccount.getBalance()
                        .compareTo(new BigDecimal("6000.00"))
        );


        // 11. VERIFY SYSTEM ACCOUNT BALANCE
        Account updatedSystemAccount =
                accountRepository.findByAccountNumber(
                        "LC-SYSTEM-INR"
                ).orElseThrow();

        assertEquals(
                0,
                updatedSystemAccount.getBalance()
                        .compareTo(
                                initialSystemBalance
                                        .add(new BigDecimal("1000.00"))
                        )
        );


        // 12. VERIFY LEDGER ENTRIES
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(
                                response.transactionId()
                        );

        assertEquals(
                2,
                ledgerEntries.size()
        );


        // 13. VERIFY SYSTEM ACCOUNT HAS DEBIT ENTRY
        assertTrue(
                ledgerEntries.stream()
                        .anyMatch(entry ->
                                entry.getEntryType()
                                        == LedgerEntryType.DEBIT
                                        && entry.getAccount()
                                        .getAccountId()
                                        .equals(
                                                updatedSystemAccount
                                                        .getAccountId()
                                        )
                        )
        );


        // 14. VERIFY CUSTOMER ACCOUNT HAS CREDIT ENTRY
        assertTrue(
                ledgerEntries.stream()
                        .anyMatch(entry ->
                                entry.getEntryType()
                                        == LedgerEntryType.CREDIT
                                        && entry.getAccount()
                                        .getAccountId()
                                        .equals(
                                                savedCustomerAccount
                                                        .getAccountId()
                                        )
                        )
        );


        // 15. FIND OUTBOX EVENTS FOR THIS TRANSACTION
        List<OutboxEvent> outboxEvents =
                outboxRepository.findByAggregateId(
                        response.transactionId()
                );


        // 16. VERIFY EXACTLY ONE OUTBOX EVENT WAS CREATED
        assertEquals(
                1,
                outboxEvents.size()
        );


        // 17. LOAD THE OUTBOX EVENT
        OutboxEvent outboxEvent =
                outboxEvents.get(0);


        // 18. VERIFY EVENT TYPE
        assertEquals(
                "DEPOSIT_COMPLETED",
                outboxEvent.getEventType()
        );


        // 19. VERIFY OUTBOX EVENT STATUS
        assertEquals(
                OutboxEventStatus.PENDING,
                outboxEvent.getStatus()
        );


        // 20. VERIFY AGGREGATE ID
        assertEquals(
                response.transactionId(),
                outboxEvent.getAggregateId()
        );


        // 21. VERIFY PAYLOAD EXISTS
        assertNotNull(
                outboxEvent.getPayload()
        );

        assertFalse(
                outboxEvent.getPayload().isBlank()
        );


        // 22. VERIFY PAYLOAD CONTAINS TRANSACTION ID
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"transactionId\":"
                                        + response.transactionId()
                        )
        );


        // 23. VERIFY PAYLOAD CONTAINS ACCOUNT ID
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"accountId\":"
                                        + savedCustomerAccount
                                        .getAccountId()
                        )
        );


        // 24. VERIFY PAYLOAD CONTAINS AMOUNT
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"amount\":1000"
                        )
        );


        // 25. VERIFY PAYLOAD CONTAINS CURRENCY
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"currency\":\"INR\""
                        )
        );


        // 26. VERIFY PAYLOAD CONTAINS REFERENCE
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"reference\":\"Deposit Outbox Test\""
                        )
        );


        // 27. VERIFY EVENT HAS NOT BEEN PUBLISHED
        assertNull(
                outboxEvent.getPublishedAt()
        );


        // 28. VERIFY INITIAL RETRY COUNT
        assertEquals(
                0,
                outboxEvent.getRetryCount()
        );


        // 29. CLEANUP OUTBOX EVENT
        outboxRepository.delete(
                outboxEvent
        );


        // 30. CLEANUP LEDGER ENTRIES
        ledgerEntryRepository.deleteAll(
                ledgerEntries
        );


        // 31. CLEANUP TRANSACTION
        transactionRepository.deleteById(
                response.transactionId()
        );


        // 32. RESTORE SYSTEM ACCOUNT BALANCE
        updatedSystemAccount.setBalance(
                initialSystemBalance
        );

        accountRepository.save(
                updatedSystemAccount
        );


        // 33. CLEANUP CUSTOMER ACCOUNT
        accountRepository.delete(
                savedCustomerAccount
        );


        // 34. CLEANUP CUSTOMER
        customerRepository.delete(
                savedCustomer
        );


        // 35. CLEAR SECURITY CONTEXT
        SecurityContextHolder.clearContext();
    }
}