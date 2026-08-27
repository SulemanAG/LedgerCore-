package com.example.ledgercore.dto.response;

import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.Currency;

import java.math.BigDecimal;

/**
 * Represents the account information exposed by the LedgerCore API.
 * <p>
 *     The response contains account information that clients are allowed ti
 *     view while keeping persistence-specific details and internal entity
 *     relationships outside the API contract.
 * </p>
 *
 * <p>
 *     The customer entity itself is not exposed. Instead, the owning customer's
 *     identifier is returned to keep the response lightweight and avoid exposing
 *     internal JPA relationships.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

public record AccountResponse (
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        Currency currency,
        AccountStatus status,
        Long customerId
){

}


