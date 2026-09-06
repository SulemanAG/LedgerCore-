package com.example.ledgercore.kafka;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.model.User;
import com.example.ledgercore.outbox.OutboxEvent;

import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.OutboxRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.repository.UserRepository;
import com.example.ledgercore.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for multi-account Kafka transfer ordering, dual per-account events,
 * and atomic monotonic version-guarded Redis balance updates.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class KafkaMultiAccountOrderingTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountBalanceRedisService redisService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Customer testCustomer;
    private final List<Account> createdAccounts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsername("userA").orElseThrow();
        testCustomer = user.getCustomer();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA", null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        // Teardown Redis keys for created accounts
        for (Account account : createdAccounts) {
            if (account.getAccountId() != null) {
                redisService.deleteBalance(account.getAccountId());
            }
        }

        // Teardown DB entries & accounts cleanly
        Set<Long> transactionIdsToClean = new HashSet<>();

        for (Account account : createdAccounts) {
            if (account.getAccountId() != null) {
                List<OutboxEvent> events = outboxRepository.findByAggregateId(account.getAccountId());
                for (OutboxEvent event : events) {
                    outboxRepository.delete(event);
                }

                List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                        .filter(e -> e.getAccount().getAccountId().equals(account.getAccountId()))
                        .toList();
                for (LedgerEntry entry : entries) {
                    if (entry.getTransaction() != null) {
                        transactionIdsToClean.add(entry.getTransaction().getTransactionId());
                    }
                    ledgerEntryRepository.delete(entry);
                }
            }
        }

        for (Long txId : transactionIdsToClean) {
            try {
                transactionRepository.deleteById(txId);
            } catch (Exception ignored) {}
        }

        for (Account account : createdAccounts) {
            if (account.getAccountId() != null) {
                try {
                    accountRepository.deleteById(account.getAccountId());
                } catch (Exception ignored) {}
            }
        }
        createdAccounts.clear();
    }

    private Account createAccount(BigDecimal initialBalance) {
        Account account = new Account();
        account.setAccountNumber("ORDER-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5));
        account.setBalance(initialBalance);
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.CUSTOMER);
        account.setCustomer(testCustomer);
        account = accountRepository.saveAndFlush(account);
        createdAccounts.add(account);
        return account;
    }

    @Test
    void testStaleOutOfOrderEventRejection() {
        Account acc = createAccount(new BigDecimal("1000.00"));
        Long accountId = acc.getAccountId();

        // 1. Initial valid update at version 5
        boolean update1 = redisService.setBalanceIfVersionGreater(accountId, new BigDecimal("1000.00"), 5L);
        assertTrue(update1, "Initial update at version 5 should succeed");
        assertEquals(0, new BigDecimal("1000.00").compareTo(redisService.getBalance(accountId)));
        assertEquals(5L, redisService.getVersion(accountId));

        // 2. Newer update at version 7
        boolean update2 = redisService.setBalanceIfVersionGreater(accountId, new BigDecimal("1200.00"), 7L);
        assertTrue(update2, "Newer update at version 7 should succeed");
        assertEquals(0, new BigDecimal("1200.00").compareTo(redisService.getBalance(accountId)));
        assertEquals(7L, redisService.getVersion(accountId));

        // 3. Out-of-order stale update at version 6
        boolean updateStale = redisService.setBalanceIfVersionGreater(accountId, new BigDecimal("1100.00"), 6L);
        assertFalse(updateStale, "Stale update at version 6 should be rejected by Lua version guard");

        // 4. Verify Redis state remains untouched at version 7 with balance 1200.00
        assertEquals(0, new BigDecimal("1200.00").compareTo(redisService.getBalance(accountId)),
                "Redis balance must NOT be overwritten by stale event");
        assertEquals(7L, redisService.getVersion(accountId),
                "Redis version must remain at 7");
    }

    @Test
    void testNewerVersionReplacesOlderProjection() {
        Account acc = createAccount(new BigDecimal("500.00"));
        Long accountId = acc.getAccountId();

        redisService.setBalanceIfVersionGreater(accountId, new BigDecimal("500.00"), 10L);
        assertEquals(10L, redisService.getVersion(accountId));

        boolean updated = redisService.setBalanceIfVersionGreater(accountId, new BigDecimal("800.00"), 11L);
        assertTrue(updated, "Higher version should replace older projection");

        assertEquals(0, new BigDecimal("800.00").compareTo(redisService.getBalance(accountId)));
        assertEquals(11L, redisService.getVersion(accountId));
    }

    @Test
    void testDualOutboxEventsForMultiSourceTransfersToSameDestination() {
        Account s1 = createAccount(new BigDecimal("5000.00"));
        Account s2 = createAccount(new BigDecimal("5000.00"));
        Account dest = createAccount(new BigDecimal("1000.00"));

        // Execute Transfer 1: S1 -> Dest ($500)
        TransferRequest req1 = new TransferRequest();
        req1.setSourceAccountId(s1.getAccountId());
        req1.setDestinationAccountId(dest.getAccountId());
        req1.setAmount(new BigDecimal("500.00"));
        req1.setCurrency(Currency.INR);
        req1.setReference("Transfer S1 to Dest");
        req1.setIdempotencyKey("KEY-S1-DEST-" + UUID.randomUUID());
        transactionService.transfer(req1);

        // Execute Transfer 2: S2 -> Dest ($300)
        TransferRequest req2 = new TransferRequest();
        req2.setSourceAccountId(s2.getAccountId());
        req2.setDestinationAccountId(dest.getAccountId());
        req2.setAmount(new BigDecimal("300.00"));
        req2.setCurrency(Currency.INR);
        req2.setReference("Transfer S2 to Dest");
        req2.setIdempotencyKey("KEY-S2-DEST-" + UUID.randomUUID());
        transactionService.transfer(req2);

        // Verify outbox events for Destination Account
        List<OutboxEvent> destEvents = outboxRepository.findByAggregateId(dest.getAccountId());
        assertEquals(2, destEvents.size(), "Destination account should have 2 outbox credit events");

        for (OutboxEvent event : destEvents) {
            assertEquals("TRANSFER_DESTINATION_CREDITED", event.getEventType());
            assertEquals(dest.getAccountId(), event.getAggregateId(), "Aggregate ID must be destination account ID");
            assertTrue(event.getPayload().contains("\"destinationAccountVersion\":"));
        }

        // Verify outbox events for S1 and S2
        List<OutboxEvent> s1Events = outboxRepository.findByAggregateId(s1.getAccountId());
        assertEquals(1, s1Events.size());
        assertEquals("TRANSFER_SOURCE_DEBITED", s1Events.get(0).getEventType());
        assertEquals(s1.getAccountId(), s1Events.get(0).getAggregateId());

        List<OutboxEvent> s2Events = outboxRepository.findByAggregateId(s2.getAccountId());
        assertEquals(1, s2Events.size());
        assertEquals("TRANSFER_SOURCE_DEBITED", s2Events.get(0).getEventType());
        assertEquals(s2.getAccountId(), s2Events.get(0).getAggregateId());
    }

    @Test
    void testRedisTeardownDeletesBothBalanceAndVersionKeys() {
        Account acc = createAccount(new BigDecimal("2000.00"));
        Long accountId = acc.getAccountId();

        redisService.setBalanceIfVersionGreater(accountId, new BigDecimal("2000.00"), 100L);
        assertNotNull(redisService.getBalance(accountId));
        assertEquals(100L, redisService.getVersion(accountId));

        // Perform teardown delete
        redisService.deleteBalance(accountId);

        assertNull(redisService.getBalance(accountId), "Balance key should be deleted");
        assertNull(redisService.getVersion(accountId), "Version key should be deleted");
    }

    @Test
    @Transactional
    void testFailedTransferRollbackLeavesNoOutboxEvents() {
        Account source = createAccount(new BigDecimal("100.00"));
        Account dest = createAccount(new BigDecimal("500.00"));

        TransferRequest invalidReq = new TransferRequest();
        invalidReq.setSourceAccountId(source.getAccountId());
        invalidReq.setDestinationAccountId(dest.getAccountId());
        invalidReq.setAmount(new BigDecimal("99999.00")); // Exceeds balance
        invalidReq.setCurrency(Currency.INR);
        invalidReq.setReference("Failed Transfer");
        invalidReq.setIdempotencyKey("FAIL-KEY-" + UUID.randomUUID());

        assertThrows(Exception.class, () -> transactionService.transfer(invalidReq));

        List<OutboxEvent> sourceEvents = outboxRepository.findByAggregateId(source.getAccountId());
        List<OutboxEvent> destEvents = outboxRepository.findByAggregateId(dest.getAccountId());

        assertTrue(sourceEvents.isEmpty(), "Rollback must leave zero outbox events for source account");
        assertTrue(destEvents.isEmpty(), "Rollback must leave zero outbox events for destination account");
    }
}
