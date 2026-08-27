package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.CreateUserRequest;
import com.example.ledgercore.dto.response.UserResponse;
import com.example.ledgercore.exception.CustomerNotFoundException;
import com.example.ledgercore.exception.DuplicateUsernameException;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.model.Role;
import com.example.ledgercore.model.User;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link UserService}
 *
 * <p>
 *     Handles creation of authentication identities and ensures that
 *     credentials are processed securely before persistence.
 * </p>
 *
 * <p>
 *     Passwords are encoded using the configured {@link org.springframework.security.crypto.password.PasswordEncoder}
 *     and are never stored in plain text or returned through API response.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse createUser(
            Long customerId,
            CreateUserRequest request
    )
    {
        Customer customer=customerRepository.findById(customerId)
                .orElseThrow(()->
                        new CustomerNotFoundException(customerId));

        if(userRepository.findByUsername(request.getUsername()).isPresent())
        {
            throw new DuplicateUsernameException(
                    request.getUsername()
            );
        }

        if (request.getRole() == Role.ADMIN) {
            throw new IllegalStateException(
                    "ADMIN users cannot be created through customer registration"
            );
        }

        User user= new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setEnabled(true);
        user.setRole(request.getRole());
        user.setCustomer(customer);
        User savedUser=userRepository.save(user);

        return mapToResponse(savedUser);
    }

    private UserResponse mapToResponse(User user)
    {
        return new UserResponse(
                user.getUserId(),
                user.getUsername(),
                user.isEnabled(),
                user.getRole(),
                user.getCustomer().getCustomerId()

        );
    }

}
