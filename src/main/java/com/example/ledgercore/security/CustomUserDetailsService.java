package com.example.ledgercore.security;


import com.example.ledgercore.model.User;
import com.example.ledgercore.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads LedgerCore users from the database for spring security authentication.
 *
 * <p>
 *     Spring Security delegates username-based authentication to this service.
 *     The service retrieves the application-level{@link com.example.ledgercore.model.User}
 *     entity and converts it into a spring security {@link org.springframework.security.core.userdetails.UserDetails}
 *     representation.
 * </p>
 *
 * <p>
 *     The database entity is intentionally kept separate from Spring Security's
 *     authentication model so that persistence concerns and security concerns remain
 *     independently maintainable.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
     private final UserRepository userRepository;

     public CustomUserDetailsService(UserRepository userRepository)
     {
         this.userRepository=userRepository;
     }

     @Override
    public UserDetails loadUserByUsername(String username)
             throws UsernameNotFoundException
     {
         User user =userRepository.findByUsername(username)
                 .orElseThrow(()->
                         new UsernameNotFoundException(
                                 "User not found"+username
                         ));

         return org.springframework.security.core.userdetails.User
                 .withUsername(user.getUsername())
                 .password(user.getPassword())
                 .authorities(
                         new SimpleGrantedAuthority(
                                 "ROLE_" + user.getRole().name()
                         )
                 )
                 .disabled(!user.isEnabled())
                 .build();
     }
}
