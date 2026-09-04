package com.example.ledgercore.transaction;

import com.example.ledgercore.dto.request.WithdrawalRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.InvalidWithdrawalException;
import com.example.ledgercore.model.*;
import com.example.ledgercore.repository.*;
import com.example.ledgercore.service.WithdrawalService;
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
 * Integration test for verifying withdrawal idempotency in LedgerCore.
 *
 * <p>
 *     Verifies that executing the same withdrawal request multiple times
 *     with the same idempotency key debits the account balance only once
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
public class WithdrawalIdempotencyTest {

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
    private WithdrawalService withdrawalService;


    @Test
    @Transactional
    void shouldProcessSameWithdrawalOnlyOnce() {

        // 1. LOAD EXISTING USER
        User user = userRepository.findByUsername("userA")
                .orElseThrow();

        Customer customer = user.getCustomer();

        // 2. CREATE CUSTOMER ACCOUNT
        Account account = new Account();
        account.setAccountNumber("WITH-IDEMP-" + System.currentTimeMillis());
        account.setBalance(new BigDecimal("10000.00"));
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

        // 4. CREATE WITHDRAWAL REQUEST
        WithdrawalRequest request = new WithdrawalRequest();
        request.setAccountId(savedAccount.getAccountId());
        request.setAmount(new BigDecimal("3000.00"));
        request.setCurrency(Currency.INR);
        request.setReference("Withdrawal Idempotency Test");

        // 5. SET IDEMPOTENCY KEY
        String idempotencyKey = "WITH-IDEMP-KEY-" + UUID.randomUUID();
        request.setIdempotencyKey(idempotencyKey);

        // 6. EXECUTE FIRST WITHDRAWAL
        TransactionResponse firstResponse = withdrawalService.withdraw(request);

        // 7. EXECUTE SAME WITHDRAWAL AGAIN
        TransactionResponse secondResponse = withdrawalService.withdraw(request);

        // 8. VERIFY BOTH REQUESTS RETURN SAME TRANSACTION
        assertEquals(firstResponse.transactionId(), secondResponse.transactionId());

        // 9. VERIFY ACCOUNT BALANCE DEBITED ONLY ONCE
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
        WithdrawalRequest differentRequest = new WithdrawalRequest();
        differentRequest.setAccountId(savedAccount.getAccountId());
        differentRequest.setAmount(new BigDecimal("5000.00")); // Different amount!
        differentRequest.setCurrency(Currency.INR);
        differentRequest.setReference("Mismatched Withdrawal");
        differentRequest.setIdempotencyKey(idempotencyKey);

        assertThrows(
                InvalidWithdrawalException.class,
                () -> withdrawalService.withdraw(differentRequest),
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
