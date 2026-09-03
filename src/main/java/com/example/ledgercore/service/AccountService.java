package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.CreateAccountRequest;
import com.example.ledgercore.dto.response.AccountResponse;

import java.util.List;

/**
 * Defines the business operations available for managing LedgerCore accounts.
 *
 * <p>The account service separates account-related business rules from the
 * REST controller and persistence layers. Account lifecycle rules, ownership
 * checks, and financial restrictions are enforced by the service layer.</p>
 *
 * <p>Account creation is performed in the context of a customer. Account
 * numbers, initial balances, lifecycle status, and other system-controlled
 * properties are managed by LedgerCore rather than supplied by API clients.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public interface AccountService {

    AccountResponse createAccount(
            Long customerId,
            CreateAccountRequest request
    );

    AccountResponse getAccountById(Long accountId);

    List<AccountResponse> getAccountsByCustomer(Long customerId);

    AccountResponse freezeAccount(Long accountId);

    AccountResponse unfreezeAccount(Long accountId);

    AccountResponse closeAccount(Long accountId);

}