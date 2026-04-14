package com.ecommerce.config;

import com.ecommerce.security.JwtAuthFilter;
import com.ecommerce.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — the central security configuration class.
 *
 * This replaces the old WebSecurityConfigurerAdapter (removed in Spring Boot 3).
 * Instead, we define @Bean methods for each piece of the security setup.
 *
 * Key decisions made here:
 *   1. Which endpoints are public vs secured
 *   2. Stateless sessions (JWT, not cookies)
 *   3. CSRF disabled (not needed for stateless REST APIs)
 *   4. Where our JwtAuthFilter runs in the filter chain
 *   5. How passwords are encoded (BCrypt)
 *
 * @EnableMethodSecurity enables @PreAuthorize on controller methods
 *   → allows per-method authorization like @PreAuthorize("hasRole('ADMIN')")
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * SecurityFilterChain — defines HTTP security rules.
     * This bean replaces the old configure(HttpSecurity http) method.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (Cross-Site Request Forgery) protection.
            // CSRF attacks exploit session cookies. Since we use stateless JWT (no cookies),
            // CSRF is not a threat for this API.
            .csrf(AbstractHttpConfigurer::disable)

            // Define which endpoints require authentication
            .authorizeHttpRequests(auth -> auth

                // Public endpoints — no token needed
                .requestMatchers("/api/auth/**").permitAll()           // login, register
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll() // browse products
                .requestMatchers("/api/payments/webhook").permitAll()  // Stripe webhook (no auth header)
                .requestMatchers("/actuator/health").permitAll()       // health check for Railway

                // Admin-only endpoints
                .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/orders/all").hasRole("ADMIN")

                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // Use STATELESS session management — Spring Security will NOT create HTTP sessions.
            // Every request must include the JWT. No cookies, no session storage on the server.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Register our DaoAuthenticationProvider (verifies email/password against DB)
            .authenticationProvider(authenticationProvider())

            // Add our JwtAuthFilter BEFORE Spring's UsernamePasswordAuthenticationFilter.
            // This ensures the JWT is processed and the SecurityContext is populated
            // before Spring Security decides if the request is authenticated.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * DaoAuthenticationProvider — authenticates users by:
     *   1. Loading user from DB via UserDetailsService
     *   2. Comparing the provided password with the stored BCrypt hash
     *
     * This is used during the login flow in AuthService.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager — the entry point for programmatic authentication.
     * We use this in AuthService to trigger the login process.
     * Spring Boot 3 requires us to expose it as a @Bean explicitly.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCryptPasswordEncoder — hashes passwords before storing them.
     * BCrypt is slow by design (cost factor = 10 by default), making brute-force attacks impractical.
     * NEVER store plain text passwords. BCrypt is the industry standard for Java apps.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
