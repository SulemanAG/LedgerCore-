package com.example.ledgercore.service;


import com.example.ledgercore.dto.request.CreateUserRequest;
import com.example.ledgercore.dto.response.UserResponse;
import org.springframework.stereotype.Service;

/**
 * Defines business operations for managing LedgerCore authentication users.
 *
 * <p>
 *     The service separates user-management business rules from the REST
 *     controller and persistence layer. Password encoding, username uniqueness,
 *     customer association, and role assignment are handle within the service
 *     layer.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

public interface UserService {

    UserResponse createUser(
            Long customerId,
            CreateUserRequest request
    );
}
