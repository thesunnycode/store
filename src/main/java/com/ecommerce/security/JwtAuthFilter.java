package com.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — runs once per HTTP request, before Spring Security's other filters.
 *
 * Responsibility: read the JWT from the Authorization header, validate it,
 * and if valid, tell Spring Security "this request is authenticated as this user."
 *
 * Request flow:
 *   HTTP Request
 *     → JwtAuthFilter (this class)
 *       → extracts token from "Authorization: Bearer <token>" header
 *       → validates token
 *       → sets authentication in SecurityContext
 *     → Spring Security checks if the authenticated user can access the endpoint
 *     → Controller method runs
 *
 * extends OncePerRequestFilter = guaranteed to run exactly once per request
 * (Spring's filter chain can sometimes call filters multiple times for forwarded requests)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain     // the chain of remaining filters
    ) throws ServletException, IOException {

        // Extract the Authorization header value (e.g., "Bearer eyJhbGci...")
        final String authHeader = request.getHeader("Authorization");

        // If there's no Authorization header or it doesn't start with "Bearer ", skip JWT auth
        // The request will proceed but remain unauthenticated (will fail if endpoint is secured)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // continue to next filter
            return;
        }

        // Extract just the token part (remove "Bearer " prefix — 7 characters)
        final String jwt = authHeader.substring(7);

        try {
            // Extract email from token payload
            final String userEmail = jwtUtil.extractUsername(jwt);

            // Only authenticate if we have an email AND the user isn't already authenticated
            // SecurityContextHolder.getContext().getAuthentication() == null means not yet authenticated
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load user from database (this also verifies the user still exists)
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // Verify the token is valid (correct user + not expired)
                if (jwtUtil.isTokenValid(jwt, userDetails)) {

                    // Create an authentication token for Spring Security
                    // UsernamePasswordAuthenticationToken(principal, credentials, authorities)
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                         // credentials = null (JWT-based, no password check here)
                                    userDetails.getAuthorities()  // user's roles/permissions
                            );

                    // Attach request metadata (IP address, session ID) to the auth token
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Store the authentication in the SecurityContext — this is what makes the user "authenticated"
                    // All subsequent security checks in this request will see this authentication
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is invalid/tampered/expired — log it and let the request proceed unauthenticated
            // Spring Security will handle the 401 response for protected endpoints
            logger.warn("JWT authentication failed: " + e.getMessage());
        }

        // Always continue to the next filter in the chain
        filterChain.doFilter(request, response);
    }
}
