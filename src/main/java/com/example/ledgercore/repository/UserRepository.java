package com.example.ledgercore.repository;

import com.example.ledgercore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository responsible for persistence operations on LedgerCore users.
 *
 * <p>The username lookup is required by the authentication layer when
 * Spring Security loads an authenticated user's credentials.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique username.
     *
     * @param username username used during authentication
     * @return matching user when one exists
     */
    Optional<User> findByUsername(String username);
}