package com.ecommerce.service;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.User;
import com.ecommerce.model.enums.Role;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService — handles user registration and login.
 *
 * Registration flow:
 *   1. Check if email is already taken
 *   2. Hash the password with BCrypt
 *   3. Save the User to DB
 *   4. Create an empty Cart for the user
 *   5. Generate and return a JWT
 *
 * Login flow:
 *   1. AuthenticationManager verifies email + password against DB
 *   2. If valid, generate and return a JWT
 *   3. If invalid, AuthenticationManager throws BadCredentialsException → 401
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new customer account.
     * @Transactional ensures that if saving the cart fails, the user save is also rolled back.
     * Both operations succeed together or both fail together (atomicity).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check for duplicate email before doing any work
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }

        // Build the User entity — password is hashed, never stored as plain text
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt hash
                .role(Role.CUSTOMER) // new registrations are always CUSTOMER by default
                .build();

        User savedUser = userRepository.save(user);

        // Create an empty cart for this user immediately after registration
        // This way we never need to create it lazily — it always exists
        Cart cart = Cart.builder().user(savedUser).build();
        cartRepository.save(cart);

        // Generate JWT for the newly registered user (auto-login after register)
        String token = jwtUtil.generateToken(savedUser);

        return buildAuthResponse(savedUser, token);
    }

    /**
     * Authenticates an existing user and returns a JWT.
     *
     * authenticationManager.authenticate() internally:
     *   1. Calls userDetailsService.loadUserByUsername(email)
     *   2. Compares provided password with stored BCrypt hash using passwordEncoder.matches()
     *   3. If match → returns Authentication object
     *   4. If no match → throws BadCredentialsException
     */
    public AuthResponse login(LoginRequest request) {
        // This line does all the authentication work — throws if credentials are wrong
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // getPrincipal() returns the UserDetails object (our User entity)
        User user = (User) authentication.getPrincipal();
        String token = jwtUtil.generateToken(user);

        return buildAuthResponse(user, token);
    }

    /** Builds the AuthResponse DTO from a User entity and its JWT token */
    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")            // standard token type for Authorization header
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
