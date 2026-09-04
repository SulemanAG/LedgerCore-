package com.example.ledgercore.transaction;

import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.InvalidTransferException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for verifying deposit idempotency in LedgerCore.
 *
 * <p>
 *     Verifies that executing the same deposit request multiple times
 *     with the same idempotency key credits the account balance only once
 *     and returns the same existing transaction.
 * </p>
 *
 * <p>
 *     Also verifies that reusing the same idempotency key for a request
 *     with a different amount is safely rejected.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
public class DepositIdempotencyTest {

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
    @Transactional
    void shouldProcessSameDepositOnlyOnce() {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer customer = user.getCustomer();

        // 2. CREATE CUSTOMER ACCOUNT
        Account account = new Account();
        account.setAccountNumber("DEP-IDEMP-" + System.currentTimeMillis());
        account.setBalance(new BigDecimal("5000.00"));
        account.setCurrency(Currency.INR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAccountType(AccountType.CUSTOMER);
        account.setCustomer(customer);

        Account savedAccount = accountRepository.save(account);

        // 3. AUTHENTICATE USER
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "userA",
                        null,
                        List.of()
                )
        );

        // 4. CREATE DEPOSIT REQUEST
        DepositRequest request = new DepositRequest();
        request.setAccountId(savedAccount.getAccountId());
        request.setAmount(new BigDecimal("2000.00"));
        request.setCurrency(Currency.INR);
        request.setReference("Deposit Idempotency Test");

        // 5. SET IDEMPOTENCY KEY
        String idempotencyKey = "DEP-IDEMP-KEY-" + UUID.randomUUID();
        request.setIdempotencyKey(idempotencyKey);

        // 6. EXECUTE FIRST DEPOSIT
        TransactionResponse firstResponse = depositService.deposit(request);

        // 7. EXECUTE SAME DEPOSIT AGAIN
        TransactionResponse secondResponse = depositService.deposit(request);

        // 8. VERIFY BOTH REQUESTS RETURN SAME TRANSACTION
        assertEquals(firstResponse.transactionId(), secondResponse.transactionId());

        // 9. VERIFY ACCOUNT BALANCE CREDITED ONLY ONCE
        Account updatedAccount = accountRepository.findById(savedAccount.getAccountId()).orElseThrow();
        assertEquals(0, updatedAccount.getBalance().compareTo(new BigDecimal("7000.00")));

        // 10. VERIFY ONLY ONE TRANSACTION RECORD WAS CREATED
        List<Transaction> transactions = transactionRepository
                .findDistinctByLedgerEntriesAccountAccountId(savedAccount.getAccountId());
        assertEquals(1, transactions.size());

        // 11. VERIFY EXACTLY TWO LEDGER ENTRIES WERE CREATED
        List<LedgerEntry> ledgerEntries = ledgerEntryRepository
                .findByTransactionTransactionId(firstResponse.transactionId());
        assertEquals(2, ledgerEntries.size());

        // 12. VERIFY TRANSACTION IS COMPLETED
        assertEquals(TransactionStatus.COMPLETED, firstResponse.status());

        // 13. VERIFY KEY REUSE WITH DIFFERENT REQUEST FAILS
        DepositRequest differentRequest = new DepositRequest();
        differentRequest.setAccountId(savedAccount.getAccountId());
        differentRequest.setAmount(new BigDecimal("5000.00")); // Different amount!
        differentRequest.setCurrency(Currency.INR);
        differentRequest.setReference("Mismatched Deposit");
        differentRequest.setIdempotencyKey(idempotencyKey);

        assertThrows(
                InvalidTransferException.class,
                () -> depositService.deposit(differentRequest),
                "Reusing idempotency key for a different request amount must be rejected"
        );

        // 14. CLEANUP LEDGER ENTRIES
        ledgerEntryRepository.deleteAll(ledgerEntries);

        // 15. CLEANUP TRANSACTION
        transactionRepository.deleteById(firstResponse.transactionId());

        // 16. CLEANUP ACCOUNT
        accountRepository.delete(savedAccount);

        // 17. CLEAR SECURITY CONTEXT
        SecurityContextHolder.clearContext();
    }
}
