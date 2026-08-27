package com.example.ledgercore.config;

import com.example.ledgercore.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures authentication and authorization rules for LedgerCore.
 *
 * <p>Spring Security is responsible for establishing the identity of the
 * authenticated user and enforcing coarse-grained authorization rules.
 * Resource ownership and financial business rules are handled separately
 * within the application layer.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Configuration
public class SecurityConfig {

    /**
     * Configures the main Spring Security filter chain.
     *
     * @param http HTTP security configuration
     * @return configured security filter chain
     * @throws Exception if the security configuration cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(requests ->
                        requests
                                .requestMatchers(HttpMethod.POST, "/customer")
                                .permitAll()

                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/customer/*/users"
                                )
                                .permitAll()

                                .requestMatchers("/error")
                                .permitAll()

                                .anyRequest()
                                .authenticated()
                )

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Configures the authentication provider used by LedgerCore.
     *
     * <p>The provider loads users from the LedgerCore database through
     * {@link CustomUserDetailsService} and verifies their passwords using
     * the configured {@link PasswordEncoder}.</p>
     *
     * @param userDetailsService database-backed user details service
     * @param passwordEncoder password encoder used for password verification
     * @return configured DAO authentication provider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    /**
     * Provides the password encoder used to securely encode user passwords.
     *
     * <p>BCrypt ensures that passwords are stored as one-way hashes rather
     * than plain-text values in the LedgerCore database.</p>
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}