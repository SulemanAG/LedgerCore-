package com.example.ledgercore.dto.request;


import com.example.ledgercore.model.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Represents a request to transfer money between two LedgerCore accounts.
 *
 * <p>
 *     The source and destination accounts are identified by their database
 *     identifiers. The authenticated user's ownership of the source account is
 *     verified by the service layer.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@Getter
@Setter
public class TransferRequest {

    @NotNull(message = "Source account ID cannot be null")
    private Long sourceAccountId;

    @NotNull(message = "Destination account ID cannot be null")
    private Long destinationAccountId;

    @NotNull(message = "Transfer amount cannot be null")
    @DecimalMin(
            value = "0.01",
            message = "Transfer amount must be greated than zero"
            )
    private BigDecimal amount;

    @NotNull(message = "Currency cannot be null")
    private Currency currency;

    private String reference;

    public TransferRequest() {
    }

    public TransferRequest(Long sourceAccountId, Long destinationAccountId,
                           BigDecimal amount, Currency currency,
                           String reference) {
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }
}
