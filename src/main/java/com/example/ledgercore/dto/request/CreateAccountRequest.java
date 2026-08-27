package com.example.ledgercore.dto.request;


import com.example.ledgercore.model.Currency;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the information required to open a new LedgerCore account.
 *
 * <p>
 *     The account number, balance, status, and version are intentionally
 *     excluded because these values are controlled by LedgerCore rather than
 *     supplied by API clients.
 * </p>
 *
 * <p>
 *     The customer associated with the account is identified through the request
 *     path rather than being accepte as the part of the request body.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */


@Getter
@Setter
public class CreateAccountRequest {

    @NotNull(message = "Currency is requried")
    private Currency currency;

    public CreateAccountRequest(){

    }

    public CreateAccountRequest(Currency currency)
    {
        this.currency=currency;
    }
}
