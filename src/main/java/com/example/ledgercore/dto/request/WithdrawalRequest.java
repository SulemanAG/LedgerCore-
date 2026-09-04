package com.example.ledgercore.dto.request;


import com.example.ledgercore.model.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request object used to withdraw money from a customer account.
 * <p>
 *     Contains the account,amount, currency, and optional reference
 *     associated with the withdrawal.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@Getter
@Setter
public class WithdrawalRequest {

    @NotNull(message = "Account ID cannot be null")
    private Long accountId;

    @NotNull(message = "Withdrawal amount cannot be null")
    @DecimalMin(
            value="0.01",
            message = "Withdrawal amount must be greater than zero"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "Withdrawal amount must have at most 2 decimal places"
    )
    private BigDecimal amount;

    @NotNull(message = "Currenct cannot be null")
    private Currency  currency;

    @Size(
            max=100,
            message = "References cannot exceed 100 characters"
    )
    private String reference;

    @NotBlank(message = "Idempotency key cannot be blank")
    @Size(
            max = 100,
            message = "Idempotency key cannot exceed 100 characters"
    )
    private String idempotencyKey;

    public WithdrawalRequest() {
    }

    public WithdrawalRequest(Long accountId, BigDecimal amount, Currency currency, String reference) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

    public WithdrawalRequest(Long accountId, BigDecimal amount, Currency currency, String reference, String idempotencyKey) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
    }
}
