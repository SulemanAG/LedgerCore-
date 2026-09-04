package com.example.ledgercore.outbox;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.*;
import com.example.ledgercore.repository.*;
import com.example.ledgercore.service.TransactionService;
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
 * for transfer operations in LedgerCore.
 *
 * <p>
 * This test verifies that a successfully completed transfer creates
 * exactly one durable outbox event associated with the financial
 * transaction.
 * </p>
 *
 * <p>
 * The test verifies the transaction, ledger entries, account balances,
 * outbox event type, status, aggregate ID, and serialized payload.
 * </p>
 *
 * <p>
 * This ensures that the transfer operation creates the durable event
 * record required by the future outbox relay and Kafka publishing
 * pipeline.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class TransferOutboxTest {

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
    private TransactionService transactionService;


    @Test
    @Transactional
    void shouldCreateOutboxEventForSuccessfulTransfer() {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer savedCustomer = user.getCustomer();


        // 2. CREATE SOURCE ACCOUNT
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "OUTBOX-SRC-" + System.currentTimeMillis()
        );
        sourceAccount.setBalance(new BigDecimal("10000.00"));
        sourceAccount.setCurrency(Currency.INR);
        sourceAccount.setStatus(AccountStatus.ACTIVE);
        sourceAccount.setAccountType(AccountType.CUSTOMER);
        sourceAccount.setCustomer(savedCustomer);

        Account savedSourceAccount =
                accountRepository.save(sourceAccount);


        // 3. CREATE DESTINATION ACCOUNT
        Account destinationAccount = new Account();

        destinationAccount.setAccountNumber(
                "OUTBOX-DEST-" + System.currentTimeMillis()
        );
        destinationAccount.setBalance(new BigDecimal("5000.00"));
        destinationAccount.setCurrency(Currency.INR);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
        destinationAccount.setAccountType(AccountType.CUSTOMER);
        destinationAccount.setCustomer(savedCustomer);

        Account savedDestinationAccount =
                accountRepository.save(destinationAccount);


        // 4. AUTHENTICATE USER
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );


        // 5. CREATE TRANSFER REQUEST
        TransferRequest request = new TransferRequest();

        request.setSourceAccountId(
                savedSourceAccount.getAccountId()
        );

        request.setDestinationAccountId(
                savedDestinationAccount.getAccountId()
        );

        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency(Currency.INR);
        request.setReference("Transfer Outbox Test");


        // 6. SET IDEMPOTENCY KEY
        request.setIdempotencyKey(
                "OUTBOX-TRANSFER-"
                        + java.util.UUID.randomUUID()
        );


        // 7. EXECUTE TRANSFER
        TransactionResponse response =
                transactionService.transfer(request);


        // 8. VERIFY TRANSACTION IS COMPLETED
        assertEquals(
                TransactionStatus.COMPLETED,
                response.status()
        );


        // 9. VERIFY SOURCE ACCOUNT BALANCE
        Account updatedSourceAccount =
                accountRepository.findById(
                        savedSourceAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedSourceAccount.getBalance()
                        .compareTo(new BigDecimal("9000.00"))
        );


        // 10. VERIFY DESTINATION ACCOUNT BALANCE
        Account updatedDestinationAccount =
                accountRepository.findById(
                        savedDestinationAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedDestinationAccount.getBalance()
                        .compareTo(new BigDecimal("6000.00"))
        );


        // 11. VERIFY LEDGER ENTRIES
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(
                                response.transactionId()
                        );

        assertEquals(
                2,
                ledgerEntries.size()
        );


        // 12. FIND OUTBOX EVENTS FOR THIS TRANSACTION
        List<OutboxEvent> outboxEvents =
                outboxRepository.findByAggregateId(
                        response.transactionId()
                );


        // 13. VERIFY EXACTLY ONE OUTBOX EVENT WAS CREATED
        assertEquals(
                1,
                outboxEvents.size()
        );


        // 14. LOAD THE OUTBOX EVENT
        OutboxEvent outboxEvent =
                outboxEvents.get(0);


        // 15. VERIFY EVENT TYPE
        assertEquals(
                "TRANSFER_COMPLETED",
                outboxEvent.getEventType()
        );


        // 16. VERIFY OUTBOX EVENT STATUS
        assertEquals(
                OutboxEventStatus.PENDING,
                outboxEvent.getStatus()
        );


        // 17. VERIFY AGGREGATE ID
        assertEquals(
                response.transactionId(),
                outboxEvent.getAggregateId()
        );


        // 18. VERIFY PAYLOAD EXISTS
        assertNotNull(
                outboxEvent.getPayload()
        );

        assertFalse(
                outboxEvent.getPayload().isBlank()
        );


        // 19. VERIFY PAYLOAD CONTAINS TRANSACTION ID
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"transactionId\":"
                                        + response.transactionId()
                        )
        );


        // 20. VERIFY PAYLOAD CONTAINS SOURCE ACCOUNT ID
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"sourceAccountId\":"
                                        + savedSourceAccount.getAccountId()
                        )
        );


        // 21. VERIFY PAYLOAD CONTAINS DESTINATION ACCOUNT ID
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"destinationAccountId\":"
                                        + savedDestinationAccount.getAccountId()
                        )
        );


        // 22. VERIFY PAYLOAD CONTAINS AMOUNT
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"amount\":1000"
                        )
        );


        // 23. VERIFY PAYLOAD CONTAINS CURRENCY
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"currency\":\"INR\""
                        )
        );


        // 24. VERIFY PAYLOAD CONTAINS REFERENCE
        assertTrue(
                outboxEvent.getPayload()
                        .contains(
                                "\"reference\":\"Transfer Outbox Test\""
                        )
        );


        // 25. VERIFY EVENT HAS NOT BEEN PUBLISHED
        assertNull(
                outboxEvent.getPublishedAt()
        );


        // 26. VERIFY INITIAL RETRY COUNT
        assertEquals(
                0,
                outboxEvent.getRetryCount()
        );


        // 27. CLEANUP OUTBOX EVENT
        outboxRepository.delete(
                outboxEvent
        );


        // 28. CLEANUP LEDGER ENTRIES
        ledgerEntryRepository.deleteAll(
                ledgerEntries
        );


        // 29. CLEANUP TRANSACTION
        transactionRepository.deleteById(
                response.transactionId()
        );


        // 30. CLEANUP ACCOUNTS
        accountRepository.delete(
                savedSourceAccount
        );

        accountRepository.delete(
                savedDestinationAccount
        );


        // 31. CLEANUP CUSTOMER
        customerRepository.delete(
                savedCustomer
        );


        // 32. CLEAR SECURITY CONTEXT
        SecurityContextHolder.clearContext();
    }
}