package com.example.ledgercore.security;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.User;
import com.example.ledgercore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthorizationService {

    private final UserRepository userRepository;

    public AccountAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isOwner(Account account) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null) {
            return false;
        }

        Long userCustomerId =
                user.getCustomer().getCustomerId();

        Long accountCustomerId =
                account.getCustomer().getCustomerId();

        System.out.println("Authenticated username: " + username);
        System.out.println("User customer ID: " + userCustomerId);
        System.out.println("Account customer ID: " + accountCustomerId);

        return userCustomerId.equals(accountCustomerId);
    }

    public boolean isCustomerOwner(Long customerId) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null) {
            return false;
        }

        return user.getCustomer()
                .getCustomerId()
                .equals(customerId);
    }
}