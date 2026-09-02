package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.InvalidTransferException;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.model.LedgerEntryType;
import com.example.ledgercore.model.Transaction;
import com.example.ledgercore.model.TransactionStatus;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.security.AccountAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service implementation responsible for deposit operations.
 *
 * <p>
 * A deposit is represented as a double-entry accounting operation.
 * The customer account receives a CREDIT entry while the internal
 * SYSTEM account receives the corresponding DEBIT entry.
 * </p>
 *
 * <p>
 * The transaction, ledger entries, and balance updates are executed
 * inside a single database transaction to preserve atomicity.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class DepositServiceImpl implements DepositService {

    private static final String SYSTEM_ACCOUNT_NUMBER =
            "LC-SYSTEM-INR";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountAuthorizationService accountAuthorizationService;

    public DepositServiceImpl(
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
    public TransactionResponse deposit(DepositRequest request) {

        validateDepositRequest(request);

        /*
         * Step 1:
         * Find the customer account receiving the money.
         */
        Account customerAccount = accountRepository
                .findById(request.getAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(request.getAccountId()));

        /*
         * Step 2:
         * Make sure the authenticated user owns the account.
         */
        if (!accountAuthorizationService.isOwner(customerAccount)) {
            throw new AccessDeniedException(
                    "You are not authorized to deposit into this account");
        }

        /*
         * Step 3:
         * The customer account must be ACTIVE.
         */
        validateAccountStatus(customerAccount);

        /*
         * Step 4:
         * The requested currency must match the account currency.
         */
        validateCurrency(customerAccount, request);

        /*
         * Step 5:
         * Find LedgerCore's internal SYSTEM account.
         */
        Account systemAccount = accountRepository
                .findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "LedgerCore SYSTEM account does not exist"));

        /*
         * Step 6:
         * Make sure the account found is actually a SYSTEM account.
         *
         * We do not want to blindly trust the account number alone.
         */
        if (systemAccount.getAccountType() != AccountType.SYSTEM) {
            throw new IllegalStateException(
                    "Configured SYSTEM account is not a SYSTEM account");
        }

        /*
         * Step 7:
         * Make sure the SYSTEM account is active.
         */
        validateAccountStatus(systemAccount);

        /*
         * Step 8:
         * The SYSTEM account must use the same currency.
         */
        if (systemAccount.getCurrency() != request.getCurrency()) {
            throw new InvalidTransferException(
                    "Deposit currency does not match SYSTEM account currency");
        }

        /*
         * Step 9:
         * Create the business transaction.
         */
        Transaction transaction = new Transaction();

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setReference(request.getReference());

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        /*
         * Step 10:
         * Create the DEBIT ledger entry for the SYSTEM account.
         */
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setAmount(request.getAmount());
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(systemAccount);

        ledgerEntryRepository.save(debitEntry);

        /*
         * Step 11:
         * Create the CREDIT ledger entry for the customer account.
         */
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setAmount(request.getAmount());
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(customerAccount);

        ledgerEntryRepository.save(creditEntry);

        /*
         * Step 12:
         * Update the current account balances.
         *
         * The ledger entries represent the accounting history.
         * The account balance represents the current state.
         */
        systemAccount.setBalance(
                systemAccount.getBalance()
                        .add(request.getAmount())
        );

        customerAccount.setBalance(
                customerAccount.getBalance()
                        .add(request.getAmount())
        );

        accountRepository.save(systemAccount);
        accountRepository.save(customerAccount);

        /*
         * Step 13:
         * Return the created transaction.
         */
        return new TransactionResponse(
                savedTransaction.getTransactionId(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getStatus(),
                savedTransaction.getCreatedAt(),
                savedTransaction.getReference()
        );
    }


    private void validateDepositRequest(DepositRequest request) {

        if (request == null) {
            throw new InvalidTransferException(
                    "Deposit request cannot be null");
        }

        if (request.getAccountId() == null) {
            throw new InvalidTransferException(
                    "Account ID cannot be null");
        }

        if (request.getAmount() == null) {
            throw new InvalidTransferException(
                    "Deposit amount cannot be null");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException(
                    "Deposit amount must be greater than zero");
        }

        if (request.getCurrency() == null) {
            throw new InvalidTransferException(
                    "Deposit currency cannot be null");
        }
    }


    private void validateAccountStatus(Account account) {

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException(
                    "Account " + account.getAccountNumber()
                            + " is not active");
        }
    }


    private void validateCurrency(
            Account customerAccount,
            DepositRequest request
    ) {

        if (customerAccount.getCurrency() != request.getCurrency()) {
            throw new InvalidTransferException(
                    "Deposit currency does not match account currency");
        }
    }
}