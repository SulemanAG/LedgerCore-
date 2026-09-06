package com.example.ledgercore.outbox;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.*;
import com.example.ledgercore.repository.*;
import com.example.ledgercore.service.TransactionService;
import com.example.ledgercore.redis.AccountBalanceRedisService;
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

    @Autowired
    private AccountBalanceRedisService redisService;


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


        // 12. FIND OUTBOX EVENTS FOR SOURCE AND DESTINATION ACCOUNTS
        List<OutboxEvent> sourceOutboxEvents =
                outboxRepository.findByAggregateId(
                        savedSourceAccount.getAccountId()
                );

        List<OutboxEvent> destinationOutboxEvents =
                outboxRepository.findByAggregateId(
                        savedDestinationAccount.getAccountId()
                );

        // 13. VERIFY DUAL OUTBOX EVENTS WERE CREATED
        assertEquals(1, sourceOutboxEvents.size());
        assertEquals(1, destinationOutboxEvents.size());

        OutboxEvent sourceEvent = sourceOutboxEvents.get(0);
        OutboxEvent destinationEvent = destinationOutboxEvents.get(0);

        // 14. VERIFY EVENT TYPES & AGGREGATE IDS
        assertEquals("TRANSFER_SOURCE_DEBITED", sourceEvent.getEventType());
        assertEquals(savedSourceAccount.getAccountId(), sourceEvent.getAggregateId());

        assertEquals("TRANSFER_DESTINATION_CREDITED", destinationEvent.getEventType());
        assertEquals(savedDestinationAccount.getAccountId(), destinationEvent.getAggregateId());

        // 15. VERIFY PAYLOAD CONTENT
        assertTrue(sourceEvent.getPayload().contains("\"sourceAccountId\":" + savedSourceAccount.getAccountId()));
        assertTrue(sourceEvent.getPayload().contains("\"sourceAccountVersion\":"));

        assertTrue(destinationEvent.getPayload().contains("\"destinationAccountId\":" + savedDestinationAccount.getAccountId()));
        assertTrue(destinationEvent.getPayload().contains("\"destinationAccountVersion\":"));

        // 16. CLEANUP OUTBOX EVENTS
        outboxRepository.delete(sourceEvent);
        outboxRepository.delete(destinationEvent);


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


        // 32. CLEANUP REDIS PROJECTIONS
        redisService.deleteBalance(
                savedSourceAccount.getAccountId()
        );

        redisService.deleteBalance(
                savedDestinationAccount.getAccountId()
        );


        // 33. CLEAR SECURITY CONTEXT
        SecurityContextHolder.clearContext();
    }
}