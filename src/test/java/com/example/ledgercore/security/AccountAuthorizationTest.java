package com.example.ledgercore.security;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for account-level authorization.
 *
 * <p>These tests verify that authenticated users can only perform
 * financial operations on accounts that belong to their customer.</p>
 *
 * <p>The authorization boundary is especially important in a financial
 * system because authentication only proves who the user is. Authorization
 * determines whether that authenticated user is allowed to access or modify
 * a particular account.</p>
 *
 * <p>These tests use the real Spring application context, repositories,
 * security context, and transaction service.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class AccountAuthorizationTest {

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
     * Verifies that a user can transfer money from an account
     * belonging to their own customer.
     */
    @Test
    void shouldAllowTransferFromOwnedAccount() {

        // 1. LOAD CUSTOMER BELONGING TO USER A
        Customer customer =
                customerRepository.findById(1L)
                        .orElseThrow();


        // 2. CREATE SOURCE ACCOUNT OWNED BY USER A
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "AUTH-SRC-" + System.currentTimeMillis()
        );

        sourceAccount.setBalance(
                new BigDecimal("5000.00")
        );

        sourceAccount.setCurrency(Currency.INR);
        sourceAccount.setStatus(AccountStatus.ACTIVE);
        sourceAccount.setAccountType(AccountType.CUSTOMER);
        sourceAccount.setCustomer(customer);

        sourceAccount =
                accountRepository.save(sourceAccount);


        // 3. CREATE DESTINATION ACCOUNT
        Account destinationAccount = new Account();

        destinationAccount.setAccountNumber(
                "AUTH-DEST-" + System.currentTimeMillis()
        );

        destinationAccount.setBalance(
                BigDecimal.ZERO
        );

        destinationAccount.setCurrency(Currency.INR);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
        destinationAccount.setAccountType(AccountType.CUSTOMER);
        destinationAccount.setCustomer(customer);

        destinationAccount =
                accountRepository.save(destinationAccount);

        Long sourceAccountId =
                sourceAccount.getAccountId();

        Long destinationAccountId =
                destinationAccount.getAccountId();


        // 4. AUTHENTICATE AS USER A
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

        Long transactionId = null;

        try {

            // 5. CREATE TRANSFER REQUEST
            TransferRequest request =
                    new TransferRequest();

            request.setSourceAccountId(sourceAccountId);
            request.setDestinationAccountId(destinationAccountId);
            request.setAmount(new BigDecimal("1000.00"));
            request.setCurrency(Currency.INR);
            request.setReference("Authorization test");


            // 6. EXECUTE TRANSFER
            TransactionResponse response =
                    transactionService.transfer(request);

            transactionId =
                    response.transactionId();


            // 7. VERIFY BALANCES
            Account sourceAfterTransfer =
                    accountRepository.findById(sourceAccountId)
                            .orElseThrow();

            Account destinationAfterTransfer =
                    accountRepository.findById(destinationAccountId)
                            .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("4000.00")
                            .compareTo(
                                    sourceAfterTransfer.getBalance()
                            ),
                    "Owned source account should be debited"
            );

            assertEquals(
                    0,
                    new BigDecimal("1000.00")
                            .compareTo(
                                    destinationAfterTransfer.getBalance()
                            ),
                    "Destination account should be credited"
            );

        } finally {

            // 8. CLEAR SECURITY CONTEXT
            SecurityContextHolder.clearContext();


            // 9. CLEAN UP LEDGER ENTRIES
            if (transactionId != null) {

                List<LedgerEntry> ledgerEntries =
                        ledgerEntryRepository
                                .findByTransactionTransactionId(
                                        transactionId
                                );

                ledgerEntryRepository.deleteAll(
                        ledgerEntries
                );


                // 10. CLEAN UP TRANSACTION
                transactionRepository.deleteById(
                        transactionId
                );
            }


            // 11. CLEAN UP TEST ACCOUNTS
            accountRepository.deleteById(
                    sourceAccountId
            );

            accountRepository.deleteById(
                    destinationAccountId
            );
        }
    }


    /**
     * Verifies that a user cannot transfer money from an account
     * belonging to another customer.
     *
     * <p>This is one of the most important security tests because
     * knowing a valid account ID must not be sufficient to perform
     * a financial operation on somebody else's account.</p>
     */
    @Test
    void shouldDenyTransferFromUnownedAccount() {

        // 1. LOAD ANOTHER CUSTOMER
        Customer customer =
                customerRepository.findById(2L)
                        .orElseThrow();


        // 2. CREATE ACCOUNT BELONGING TO CUSTOMER 2
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "AUTH-OTHER-SRC-" +
                        System.currentTimeMillis()
        );

        sourceAccount.setBalance(
                new BigDecimal("5000.00")
        );

        sourceAccount.setCurrency(Currency.INR);
        sourceAccount.setStatus(AccountStatus.ACTIVE);
        sourceAccount.setAccountType(AccountType.CUSTOMER);
        sourceAccount.setCustomer(customer);

        sourceAccount =
                accountRepository.save(sourceAccount);


        // 3. LOAD USER A'S CUSTOMER
        Customer userACustomer =
                customerRepository.findById(1L)
                        .orElseThrow();


        // 4. CREATE DESTINATION ACCOUNT
        Account destinationAccount = new Account();

        destinationAccount.setAccountNumber(
                "AUTH-OTHER-DEST-" +
                        System.currentTimeMillis()
        );

        destinationAccount.setBalance(
                BigDecimal.ZERO
        );

        destinationAccount.setCurrency(Currency.INR);
        destinationAccount.setStatus(AccountStatus.ACTIVE);
        destinationAccount.setAccountType(AccountType.CUSTOMER);
        destinationAccount.setCustomer(userACustomer);

        destinationAccount =
                accountRepository.save(destinationAccount);

        Long sourceAccountId =
                sourceAccount.getAccountId();

        Long destinationAccountId =
                destinationAccount.getAccountId();


        // 5. AUTHENTICATE AS USER A
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

        try {

            // 6. CREATE TRANSFER REQUEST
            TransferRequest request =
                    new TransferRequest();

            request.setSourceAccountId(sourceAccountId);
            request.setDestinationAccountId(destinationAccountId);
            request.setAmount(new BigDecimal("1000.00"));
            request.setCurrency(Currency.INR);
            request.setReference(
                    "Unauthorized transfer test"
            );


            // 7. VERIFY ACCESS IS DENIED
            assertThrows(
                    AccessDeniedException.class,
                    () -> transactionService.transfer(request),
                    "User A must not transfer from another customer's account"
            );


            // 8. VERIFY SOURCE BALANCE WAS NOT MODIFIED
            Account sourceAfterAttempt =
                    accountRepository.findById(sourceAccountId)
                            .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("5000.00")
                            .compareTo(
                                    sourceAfterAttempt.getBalance()
                            ),
                    "Unauthorized transfer must not modify source balance"
            );

        } finally {

            // 9. CLEAR SECURITY CONTEXT
            SecurityContextHolder.clearContext();


            // 10. CLEAN UP TEST ACCOUNTS
            accountRepository.deleteById(
                    sourceAccountId
            );

            accountRepository.deleteById(
                    destinationAccountId
            );
        }
    }
}