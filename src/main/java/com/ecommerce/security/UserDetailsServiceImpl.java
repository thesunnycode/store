package com.ecommerce.security;

import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl — bridges our app's User entity with Spring Security.
 *
 * Spring Security's authentication mechanism calls loadUserByUsername() every time
 * it needs to verify who a request belongs to. We tell it: "look up the user by
 * email in our database."
 *
 * Since User already implements UserDetails, we can return it directly.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Called by Spring Security during authentication.
     * "username" here means email — we use email as the unique identifier.
     *
     * @Transactional ensures the DB session stays open long enough to load
     * any lazy-loaded relationships on the User object.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));
    }
}
