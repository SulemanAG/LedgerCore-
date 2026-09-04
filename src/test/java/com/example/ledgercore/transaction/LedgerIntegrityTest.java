package com.example.ledgercore.transaction;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.LedgerEntryResponse;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.model.LedgerEntryType;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for double-entry ledger integrity.
 *
 * <p>These tests verify that every completed financial transaction
 * maintains the fundamental double-entry accounting invariant:
 * total debit amount must equal total credit amount.</p>
 *
 * <p>A transfer in LedgerCore produces one DEBIT ledger entry for
 * the source account and one CREDIT ledger entry for the destination
 * account. Both entries must contain the same monetary amount.</p>
 *
 * <p>The tests use the real Spring application context and database
 * so that the actual transaction and ledger implementation is tested.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class LedgerIntegrityTest {

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
     * Verifies that a completed transfer contains balanced
     * DEBIT and CREDIT ledger entries.
     *
     * <p>The test creates two accounts, performs a transfer,
     * retrieves the resulting ledger entries, and verifies that
     * the total DEBIT amount is exactly equal to the total CREDIT
     * amount.</p>
     */
    @Test
    void shouldMaintainBalancedLedgerForTransfer() {


        // 1. LOAD TEST CUSTOMER
        Customer customer =
                customerRepository.findById(1L)
                        .orElseThrow();



        // 2. CREATE SOURCE ACCOUNT
        Account sourceAccount = new Account();

        sourceAccount.setAccountNumber(
                "LEDGER-SOURCE-" + System.currentTimeMillis()
        );

        sourceAccount.setBalance(
                new BigDecimal("10000.00")
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
                "LEDGER-DEST-" + System.currentTimeMillis()
        );

        destinationAccount.setBalance(
                new BigDecimal("5000.00")
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



        // 4. CREATE AUTHENTICATION CONTEXT
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

        // Keep the transaction response outside the try block
        // so that cleanup can access the transaction ID.
        TransactionResponse response = null;

        try {


            // 5. CREATE TRANSFER REQUEST
            TransferRequest request =
                    new TransferRequest();

            request.setSourceAccountId(sourceAccountId);
            request.setDestinationAccountId(destinationAccountId);
            request.setAmount(new BigDecimal("1000.00"));
            request.setCurrency(Currency.INR);
            request.setReference("Ledger integrity test");



            // 6. EXECUTE TRANSFER
            response =
                    transactionService.transfer(request);



            // 7. VERIFY TRANSACTION WAS CREATED
            assertNotNull(
                    response.transactionId(),
                    "Transaction ID should be generated"
            );

            assertEquals(
                    "COMPLETED",
                    response.status().name(),
                    "Transfer should be completed"
            );



            // 8. RETRIEVE LEDGER ENTRIES
            List<LedgerEntryResponse> ledgerEntries =
                    transactionService
                            .getLedgerEntriesByTransaction(
                                    response.transactionId()
                            );



            // 9. VERIFY EXACTLY TWO ENTRIES EXIST
            assertEquals(
                    2,
                    ledgerEntries.size(),
                    "A simple transfer must contain exactly " +
                            "one DEBIT and one CREDIT entry"
            );



            // 10. CALCULATE TOTAL DEBIT
            BigDecimal totalDebit =
                    ledgerEntries.stream()
                            .filter(entry ->
                                    entry.entryType()
                                            == LedgerEntryType.DEBIT
                            )
                            .map(LedgerEntryResponse::amount)
                            .reduce(
                                    BigDecimal.ZERO,
                                    BigDecimal::add
                            );



            // 11. CALCULATE TOTAL CREDIT
            BigDecimal totalCredit =
                    ledgerEntries.stream()
                            .filter(entry ->
                                    entry.entryType()
                                            == LedgerEntryType.CREDIT
                            )
                            .map(LedgerEntryResponse::amount)
                            .reduce(
                                    BigDecimal.ZERO,
                                    BigDecimal::add
                            );



            // 12. VERIFY DOUBLE-ENTRY INVARIANT
            assertEquals(
                    0,
                    totalDebit.compareTo(totalCredit),
                    "Total DEBIT must equal total CREDIT"
            );



            // 13. VERIFY BOTH ENTRIES MATCH TRANSFER AMOUNT
            assertEquals(
                    0,
                    new BigDecimal("1000.00")
                            .compareTo(totalDebit),
                    "Total DEBIT must equal transfer amount"
            );

            assertEquals(
                    0,
                    new BigDecimal("1000.00")
                            .compareTo(totalCredit),
                    "Total CREDIT must equal transfer amount"
            );

        } finally {


            // 14. CLEAR SECURITY CONTEXT
            SecurityContextHolder.clearContext();



            // 15. CLEAN UP LEDGER ENTRIES
            if (response != null) {

                List<com.example.ledgercore.model.LedgerEntry> ledgerEntries =
                        ledgerEntryRepository
                                .findByTransactionTransactionId(
                                        response.transactionId()
                                );

                ledgerEntryRepository.deleteAll(ledgerEntries);



                // 16. CLEAN UP TRANSACTION
                transactionRepository.deleteById(
                        response.transactionId()
                );
            }



            // 17. CLEAN UP TEST ACCOUNTS
            accountRepository.deleteById(sourceAccountId);
            accountRepository.deleteById(destinationAccountId);
        }
    }
}