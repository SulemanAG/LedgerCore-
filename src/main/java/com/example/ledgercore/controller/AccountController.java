package com.example.ledgercore.controller;

import com.example.ledgercore.dto.request.CreateAccountRequest;
import com.example.ledgercore.dto.response.AccountResponse;
import com.example.ledgercore.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller responsible for account-related operations.
 *
 * <p>The controller acts as the HTTP boundary for LedgerCore account
 * operations and delegates business logic to {@link AccountService}.
 * Account entities are not exposed directly through the API; DTOs are
 * used to maintain separation between the API and persistence layers.</p>
 *
 * <p>Accounts are created in the context of a customer, while account
 * numbers, balances, and lifecycle states are controlled by LedgerCore.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }


    @PostMapping("/customer/{customerId}")
    public ResponseEntity<AccountResponse> createAccount(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateAccountRequest request) {

        AccountResponse response =
                accountService.createAccount(customerId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long accountId) {

        return ResponseEntity.ok(
                accountService.getAccountById(accountId)
        );
    }


    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByCustomer(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                accountService.getAccountsByCustomer(customerId)
        );
    }


    @PatchMapping("/{accountId}/freeze")
    public ResponseEntity<AccountResponse> freezeAccount(
            @PathVariable Long accountId) {

        return ResponseEntity.ok(
                accountService.freezeAccount(accountId)
        );
    }

    @PatchMapping("/{accountId}/unfreeze")
    public ResponseEntity<AccountResponse> unfreezeAccount(
            @PathVariable Long accountId){
        return ResponseEntity.ok(
                accountService.unfreezeAccount(accountId)
        );
    }


    @PatchMapping("/{accountId}/close")
    public ResponseEntity<AccountResponse> closeAccount(
            @PathVariable Long accountId) {

        return ResponseEntity.ok(
                accountService.closeAccount(accountId)
        );
    }
}