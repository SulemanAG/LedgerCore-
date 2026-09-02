package com.example.ledgercore.dto.request;


import com.example.ledgercore.model.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request DTO used to deposit money into a customer account.
 * <p>
 *     The request contains only the information that a customer
 *     is allowed to provide.The SYSTEM account used as the accounting counterpart
 *     is determined internally by LedgerCore.
 * </p>
 *
 * <p>
 *     The deposit amount must be greater than zero and may contain a maximum of two digits
 *     after the decimal point for normal currency usage.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@Getter
@Setter
public class DepositRequest {

    @NotNull(message = "Account ID cannot be null")
    private Long accountId;

    @NotNull(message = "Deposit amount cannot be null")
    @DecimalMin(
            value="0.01",
            message = "Deposit amount must be greater than zero"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "Deposit amount must have atmost 2 decimal places"
    )
    private BigDecimal amount;

    @NotNull(message = "Currency cannot be null")
    private Currency currency;

    @Size(
            max=100,
            message = "Reference cannot exceed 100 characters"
    )
    private String reference;

    public DepositRequest() {
    }

    public DepositRequest(Long accountId, BigDecimal amount, Currency currency, String reference) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }
}
