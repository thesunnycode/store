package com.ecommerce.controller;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.model.User;
import com.ecommerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * CartController — manages the authenticated user's shopping cart.
 *
 * All endpoints require a valid JWT (enforced by SecurityConfig).
 *
 * @AuthenticationPrincipal User user
 *   → Spring Security injects the currently logged-in User object directly.
 *   → This comes from the SecurityContext which was populated by JwtAuthFilter.
 *   → We don't need to query the DB again — the full User object is already available.
 *
 * Endpoints:
 *   GET    /api/cart                      → view current cart
 *   POST   /api/cart/items                → add item to cart
 *   PUT    /api/cart/items/{cartItemId}   → update item quantity
 *   DELETE /api/cart/items/{cartItemId}   → remove item from cart
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** GET /api/cart — returns the current user's full cart with total */
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal User user) {
        CartResponse cart = cartService.getCart(user);
        return ResponseEntity.ok(ApiResponse.success("Cart fetched successfully", cart));
    }

    /** POST /api/cart/items — add a product to cart (or increase quantity if already present) */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CartItemRequest request) {
        CartResponse cart = cartService.addItem(user, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    /**
     * PUT /api/cart/items/{cartItemId}?quantity=3 — update item quantity.
     * Setting quantity=0 removes the item.
     */
    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal User user,
            @PathVariable Long cartItemId,
            @RequestParam Integer quantity) {
        CartResponse cart = cartService.updateItem(user, cartItemId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Cart updated", cart));
    }

    /** DELETE /api/cart/items/{cartItemId} — remove a specific item */
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal User user,
            @PathVariable Long cartItemId) {
        CartResponse cart = cartService.removeItem(user, cartItemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }
}
