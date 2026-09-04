package com.example.ledgercore.transaction;

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
 * Integration test for verifying transfer idempotency in LedgerCore.
 *
 * <p>
 *     This test verifies that submitting the same transfer request multiple
 *     times with the same idempotency key does not create duplicate financial
 *     transactions or move money more than once.
 * </p>
 *
 * <p>
 *     The test executes the same transfer twice and verifies that both
 *     requests return the same transaction, the account balances are updated
 *     only once, exactly one transaction record is created, and exactly two
 *     ledger entries are generated for the transfer.
 * </p>
 *
 * <p>
 *     This ensures that client retries caused by network failures or lost
 *     responses do not result in duplicate financial operations.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class IdempotencyTest {

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


    @Test
    @Transactional
    void shouldProcessSameTransferOnlyOnce() {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer savedCustomer = user.getCustomer();

        // 2. CREATE SOURCE ACCOUNT
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "IDEMP-SRC-" + System.currentTimeMillis()
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
                "IDEMP-DEST-" + System.currentTimeMillis()
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
        request.setReference("Idempotency Test");

        // 6. SET IDEMPOTENCY KEY
        request.setIdempotencyKey("IDEMP-TEST-" + java.util.UUID.randomUUID());


        // 7. EXECUTE FIRST TRANSFER
        TransactionResponse firstResponse =
                transactionService.transfer(request);


        // 8. EXECUTE SAME TRANSFER AGAIN
        TransactionResponse secondResponse =
                transactionService.transfer(request);


        // 9. VERIFY BOTH REQUESTS RETURN SAME TRANSACTION
        assertEquals(
                firstResponse.transactionId(),
                secondResponse.transactionId()
        );


        // 10. VERIFY SOURCE ACCOUNT BALANCE
        Account updatedSourceAccount =
                accountRepository.findById(
                        savedSourceAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedSourceAccount.getBalance()
                        .compareTo(new BigDecimal("9000.00"))
        );


        // 11. VERIFY DESTINATION ACCOUNT BALANCE
        Account updatedDestinationAccount =
                accountRepository.findById(
                        savedDestinationAccount.getAccountId()
                ).orElseThrow();

        assertEquals(
                0,
                updatedDestinationAccount.getBalance()
                        .compareTo(new BigDecimal("6000.00"))
        );


        // 12. VERIFY ONLY ONE TRANSACTION WAS CREATED
        List<Transaction> transactions =
                transactionRepository
                        .findDistinctByLedgerEntriesAccountAccountId(
                                savedSourceAccount.getAccountId()
                        );

        assertEquals(1, transactions.size());


        // 13. VERIFY EXACTLY TWO LEDGER ENTRIES WERE CREATED
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(
                                firstResponse.transactionId()
                        );

        assertEquals(2, ledgerEntries.size());


        // 14. VERIFY TRANSACTION IS COMPLETED
        assertEquals(
                TransactionStatus.COMPLETED,
                firstResponse.status()
        );


        // 15. CLEANUP LEDGER ENTRIES
        ledgerEntryRepository.deleteAll(ledgerEntries);


        // 16. CLEANUP TRANSACTION
        transactionRepository.deleteById(
                firstResponse.transactionId()
        );


        // 17. CLEANUP ACCOUNTS
        accountRepository.delete(savedSourceAccount);
        accountRepository.delete(savedDestinationAccount);


        // 18. CLEANUP CUSTOMER
        customerRepository.delete(savedCustomer);


        // 19. CLEAR SECURITY CONTEXT
        SecurityContextHolder.clearContext();
    }
}