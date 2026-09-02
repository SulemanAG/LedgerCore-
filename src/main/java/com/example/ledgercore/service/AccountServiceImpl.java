package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.CreateAccountRequest;
import com.example.ledgercore.dto.response.AccountResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.CustomerNotFoundException;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.security.AccountAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Default implementation of {@link AccountService}.
 *
 * <p>Provides the business logic for creating and managing LedgerCore
 * accounts. Customer ownership is verified before an account is created,
 * while account lifecycle transitions are controlled by the service layer.</p>
 *
 * <p>System-controlled properties such as the account number, initial balance,
 * and account status are assigned by LedgerCore rather than trusted from
 * client input.</p>
 *
 * <p>Resource-level authorization is performed before exposing account
 * information to ensure that customers can access only their own accounts.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountAuthorizationService accountAuthorizationService;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            AccountAuthorizationService accountAuthorizationService
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    @Override
    public AccountResponse createAccount(
            Long customerId,
            CreateAccountRequest request
    ) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId));

        Account account = new Account();

        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency(request.getCurrency());
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomer(customer);
        account.setAccountType(AccountType.CUSTOMER);

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }

    @Override
    public AccountResponse getAccountById(Long accountId) {

        Account account = findAccount(accountId);

        if (!accountAuthorizationService.isOwner(account)) {
            throw new AccessDeniedException(
                    "You are not authorized to access this account"
            );
        }

        return mapToResponse(account);
    }

    @Override
    public List<AccountResponse> getAccountsByCustomer(Long customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId));

        if (!accountAuthorizationService.isCustomerOwner(customerId)) {
            throw new AccessDeniedException(
                    "You are not authorized to access this customer's accounts"
            );
        }

        return customer.getAccounts()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public AccountResponse freezeAccount(Long accountId) {

        Account account = findAccount(accountId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Closed account cannot be frozen"
            );
        }

        account.setStatus(AccountStatus.FROZEN);

        return mapToResponse(
                accountRepository.save(account)
        );
    }

    @Override
    public AccountResponse closeAccount(Long accountId) {

        Account account = findAccount(accountId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Account is already closed"
            );
        }

        account.setStatus(AccountStatus.CLOSED);

        return mapToResponse(
                accountRepository.save(account)
        );
    }


    private Account findAccount(Long accountId) {

        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountId));
    }


    private String generateAccountNumber() {

        return "LC" + System.currentTimeMillis();
    }


    private AccountResponse mapToResponse(Account account) {

        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCustomer().getCustomerId()
        );
    }
}