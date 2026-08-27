package com.example.ledgercore.security;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.User;
import com.example.ledgercore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Provides resource-level authorization checks for LedgerCore accounts.
 *
 * <p>Spring Security establishes the authenticated identity, while this
 * service determines whether that identity is authorized to access a
 * particular account.</p>
 *
 * <p>Account authorization is based on ownership rather than trusting a
 * customer identifier supplied by the client.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class AccountAuthorizationService {

    private final UserRepository userRepository;

    public AccountAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Determines whether the currently authenticated user owns the
     * specified account.
     *
     * @param account account whose ownership is being checked
     * @return true when the authenticated user owns the account
     */
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

        return user.getCustomer()
                .getCustomerId()
                .equals(account.getCustomer().getCustomerId());
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