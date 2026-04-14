package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.AuthResponse;
import com.ecommerce.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — handles user registration and login.
 *
 * Endpoints:
 *   POST /api/auth/register → create new CUSTOMER account, returns JWT
 *   POST /api/auth/login    → authenticate existing user, returns JWT
 *
 * Both endpoints are PUBLIC (no token required) — configured in SecurityConfig.
 *
 * @RestController = @Controller + @ResponseBody
 *   → All methods return JSON automatically (no need for @ResponseBody per method)
 * @RequestMapping → base URL prefix for all endpoints in this controller
 * @RequiredArgsConstructor → Lombok injects all final fields via constructor (not @Autowired)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new customer account.
     *
     * @Valid → triggers validation on the RegisterRequest DTO.
     * If validation fails, Spring throws MethodArgumentNotValidException
     * which is caught by GlobalExceptionHandler → returns 400 with field errors.
     *
     * Returns 201 CREATED (not 200 OK) because we created a new resource.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", authResponse));
    }

    /**
     * Login with existing credentials.
     * Returns 200 OK with JWT if credentials are valid.
     * Returns 401 if credentials are wrong (handled by GlobalExceptionHandler).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }
}
