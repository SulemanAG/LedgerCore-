package com.example.ledgercore.controller;

import com.example.ledgercore.dto.request.CreateUserRequest;
import com.example.ledgercore.dto.response.UserResponse;
import com.example.ledgercore.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller responsible for authentication-user operations.
 *
 * <p>
 *     The controller provides the HTTP boundary for creating authentication
 *     identities associated with LedgerCore customers, Business rules such as
 *     username uniqueness, password encoding, and customer association are
 *     delegated to {@link com.example.ledgercore.service.UserService}
 * </p>
 *
 * <p>
 *     Sensitive credentials are never returned through the API response.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@RestController
@RequestMapping("/customer/{customerId}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @PathVariable Long customerId,
            @Valid @RequestBody CreateUserRequest request
            )
    {
        UserResponse response= userService.createUser(customerId,request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

}
