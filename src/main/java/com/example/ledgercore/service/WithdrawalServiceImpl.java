package com.example.ledgercore.service;


import com.example.ledgercore.dto.request.WithdrawalRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.InsufficientFundsException;
import com.example.ledgercore.exception.InvalidWithdrawalException;
import com.example.ledgercore.model.*;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.security.AccountAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service responsible for executing withdrawal operations.
 *
 * <p>
 *     A withdrawal removes money from a customer account and records
 *     the corresponding accounting entries.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class WithdrawalServiceImpl implements  WithdrawalService{

    private static final String SYSTEM_ACCOUNT_NUMBER="LC-SYSTEM-INR";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountAuthorizationService accountAuthorizationService;

    public WithdrawalServiceImpl(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountAuthorizationService accountAuthorizationService
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request) {

        // 1. Validate the withdrawal request
        validateWithdrawalRequest(request);

        // 2. Load the customer account
        Account customerAccount = accountRepository
                .findById(request.getAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(request.getAccountId())
                );

        // 3. Verify that the authenticated user owns the account
        if (!accountAuthorizationService.isOwner(customerAccount)) {

            throw new AccessDeniedException(
                    "You are not authorized to withdraw money from this account"
            );
        }

        // 4. Verify that the account is active
        if(customerAccount.getStatus()!= AccountStatus.ACTIVE)
        {
            throw new InvalidWithdrawalException(
                    "Account "+customerAccount.getAccountNumber()
                    +"Is not ACTIVE"
            );
        }

        //5. Validate Currency
        if(customerAccount.getCurrency()!=request.getCurrency())
        {
            throw  new InvalidWithdrawalException(
                    "Withdrawal currecny does not match account currency"
            );
        }

        // 6. Check for sufficient funds
        if(customerAccount.getBalance()
                .compareTo(request.getAmount())<0)
        {
            throw  new InsufficientFundsException(
                    "Insufficient funds in the account"
            );
        }

        // 7. Check for the system account
        Account systemAccount = accountRepository
                .findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(
                        ()-> new IllegalStateException(
                                "SYSTEM account not found!"
                        )
                );

        // 8. Verify that the SYSTEM account is correctly configured
        if (systemAccount.getAccountType() != AccountType.SYSTEM) {

            throw new IllegalStateException(
                    "Configured SYSTEM account is not a SYSTEM account"
            );
        }

        if (systemAccount.getStatus() != AccountStatus.ACTIVE) {

            throw new IllegalStateException(
                    "SYSTEM account is not active"
            );
        }

        if (systemAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidWithdrawalException(
                    "SYSTEM account currency does not match withdrawal currency"
            );
        }


        // 9. Create the withdrawal transaction
        Transaction transaction = new Transaction();

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setReference(request.getReference());

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 10. Create DEBIT ledger entry for the customer account
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setAmount(request.getAmount());
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(customerAccount);

        ledgerEntryRepository.save(debitEntry);

        // 11. Create CREDIT ledger entry for the SYSTEM account
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setAmount(request.getAmount());
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(systemAccount);

        ledgerEntryRepository.save(creditEntry);

        // 12. Reduce the customer account balance
        customerAccount.setBalance(
                customerAccount.getBalance()
                        .subtract(request.getAmount())
        );

        // 13. Increase the SYSTEM account balance
        systemAccount.setBalance(
                systemAccount.getBalance()
                        .add(request.getAmount())
        );

        // 14. Save both updated account balances
        accountRepository.save(customerAccount);
        accountRepository.save(systemAccount);

        // 15. Return the completed withdrawal transaction
        return new TransactionResponse(
                savedTransaction.getTransactionId(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getStatus(),
                savedTransaction.getCreatedAt(),
                savedTransaction.getReference()
        );
    }

    /**
     * Validates withdrawal request fields that do not require
     * database access.
     *
     * @param request withdrawal request to validate
     */
    private void validateWithdrawalRequest(WithdrawalRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Withdrawal request cannot be null"
            );
        }

        if (request.getAccountId() == null) {
            throw new IllegalArgumentException(
                    "Account ID cannot be null"
            );
        }

        if (request.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Withdrawal amount cannot be null"
            );
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount must be greater than zero"
            );
        }

        if (request.getCurrency() == null) {
            throw new IllegalArgumentException(
                    "Withdrawal currency cannot be null"
            );
        }
    }
}
