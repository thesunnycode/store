package com.ecommerce.controller;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.model.User;
import com.ecommerce.model.enums.OrderStatus;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * OrderController — handles checkout and order management.
 *
 * CUSTOMER endpoints:
 *   POST /api/orders/checkout     → place order from cart
 *   GET  /api/orders/my           → view own order history
 *   GET  /api/orders/my/{id}      → view a specific own order
 *
 * ADMIN endpoints:
 *   GET  /api/orders/all          → view all orders (all customers)
 *   PUT  /api/orders/{id}/status  → update order status (e.g., mark SHIPPED)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders/checkout
     * Places an order from the user's current cart.
     * Returns 201 Created with the new order details.
     * The order starts with status PENDING — payment is handled separately.
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CheckoutRequest request) {
        OrderResponse order = orderService.checkout(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", order));
    }

    /**
     * GET /api/orders/my?page=0&size=10
     * Returns the logged-in user's order history, newest first.
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<OrderResponse> orders = orderService.getMyOrders(user, page, size);
        return ResponseEntity.ok(ApiResponse.success("Your orders", orders));
    }

    /**
     * GET /api/orders/my/{id}
     * Returns a specific order — only if it belongs to the logged-in user.
     * If they try to view someone else's order, OrderService returns 404 (not 403)
     * to avoid leaking the existence of other users' orders.
     */
    @GetMapping("/my/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        OrderResponse order = orderService.getMyOrderById(user, id);
        return ResponseEntity.ok(ApiResponse.success("Order details", order));
    }

    /**
     * GET /api/orders/all?page=0&size=20 — ADMIN only
     * Returns all orders across all customers.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<OrderResponse> orders = orderService.getAllOrders(page, size);
        return ResponseEntity.ok(ApiResponse.success("All orders", orders));
    }

    /**
     * PUT /api/orders/{id}/status?status=SHIPPED — ADMIN only
     * Updates the order status (CONFIRMED → SHIPPED → DELIVERED).
     * Status is passed as a query param, which Spring auto-converts to the OrderStatus enum.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        OrderResponse order = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Order status updated to " + status, order));
    }
}
