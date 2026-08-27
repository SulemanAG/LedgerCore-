package com.example.ledgercore.dto.response;

import com.example.ledgercore.model.Role;

/**
 * Represents the non-sensitive user information exposed by the LedgerCore
 * API.
 *
 * <p>
 *     The passowrd is intentionally excluded from the response. Authentication
 *     credentials must never be returned to API clients after user creation.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

public record UserResponse(
        Long userId,
        String username,
        boolean enabled,
        Role role,
        Long customerId
) {

}
