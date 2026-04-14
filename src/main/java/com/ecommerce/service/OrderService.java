package com.ecommerce.service;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.*;
import com.ecommerce.model.enums.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * OrderService — handles the checkout flow and order management.
 *
 * Checkout flow:
 *   1. Load user's cart — fail if empty
 *   2. Validate stock for each item
 *   3. Create Order with OrderItems (price snapshot at purchase time)
 *   4. Deduct stock for each product
 *   5. Clear the cart
 *   6. Return the new order (status = PENDING, awaiting payment)
 *
 * Payment is handled separately by PaymentService (Stripe).
 * After payment succeeds, the order status is updated to CONFIRMED.
 *
 * @Transactional on checkout is CRITICAL — if any step fails (e.g., stock update),
 * the entire transaction rolls back. No partial orders, no stock deducted without an order.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;

    /**
     * Places an order from the current cart.
     * This is atomic — all or nothing.
     */
    @Transactional
    public OrderResponse checkout(User user, CheckoutRequest request) {
        Cart cart = cartService.getOrCreateCart(user);

        if (cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty. Add items before checking out.");
        }

        // Build order with snapshot of current prices
        Order order = Order.builder()
                .user(user)
                .shippingAddress(request.getShippingAddress())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)  // calculated below
                .build();

        // Create OrderItems from CartItems — snapshot product name and price
        List<OrderItem> orderItems = cart.getCartItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();

                    // Final stock validation before committing the order
                    if (product.getStockQuantity() < cartItem.getQuantity()) {
                        throw new BadRequestException(
                                "Insufficient stock for product: " + product.getName() +
                                ". Available: " + product.getStockQuantity());
                    }

                    // Deduct stock — this is the moment inventory decreases
                    product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());

                    return OrderItem.builder()
                            .order(order)
                            .product(product)
                            .productName(product.getName())           // snapshot name
                            .priceAtPurchase(product.getPrice())      // snapshot price
                            .quantity(cartItem.getQuantity())
                            .build();
                })
                .toList();

        order.setOrderItems(orderItems);

        // Calculate total: sum of (price × quantity) for each item
        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        // Clear the cart after successful order creation
        cartService.clearCart(cart);

        return toOrderResponse(savedOrder);
    }

    /** Customer: view their own orders, most recent first */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(User user, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findByUser(user, pageable)
                .map(this::toOrderResponse);
    }

    /** Customer: view a specific order (verified to belong to them) */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderById(User user, Long orderId) {
        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return toOrderResponse(order);
    }

    /** Admin: view ALL orders across all customers */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findAll(pageable)
                .map(this::toOrderResponse);
    }

    /** Admin: update order status (e.g., mark as SHIPPED or DELIVERED) */
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        order.setStatus(newStatus);
        return toOrderResponse(orderRepository.save(order));
    }

    /**
     * Called by PaymentService when Stripe confirms payment.
     * Updates status to CONFIRMED and stores the Stripe payment ID.
     */
    @Transactional
    public void confirmPayment(String stripePaymentIntentId) {
        Order order = orderRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for payment: " + stripePaymentIntentId));
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    /**
     * Links a Stripe PaymentIntent ID to an order.
     * Called right after the PaymentIntent is created in PaymentService.
     */
    @Transactional
    public void linkPaymentIntent(Long orderId, String paymentIntentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStripePaymentIntentId(paymentIntentId);
        orderRepository.save(order);
    }

    /** Converts Order entity to OrderResponse DTO */
    private OrderResponse toOrderResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .orderItemId(item.getId())
                        .productName(item.getProductName())          // use snapshot, not live product name
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerName(order.getUser().getName())
                .customerEmail(order.getUser().getEmail())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .stripePaymentIntentId(order.getStripePaymentIntentId())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
