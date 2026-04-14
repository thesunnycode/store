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

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartService cartService;

    @Transactional
    public OrderResponse checkout(User user, CheckoutRequest request) {
        Cart cart = cartService.getOrCreateCart(user);

        if (cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty. Add items before checking out.");
        }

        Order order = Order.builder()
                .user(user)
                .shippingAddress(request.getShippingAddress())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        List<OrderItem> orderItems = cart.getCartItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();

                    if (product.getStockQuantity() < cartItem.getQuantity()) {
                        throw new BadRequestException(
                                "Insufficient stock for product: " + product.getName() +
                                ". Available: " + product.getStockQuantity());
                    }

                    product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());

                    return OrderItem.builder()
                            .order(order)
                            .product(product)
                            .productName(product.getName())
                            .priceAtPurchase(product.getPrice())
                            .quantity(cartItem.getQuantity())
                            .build();
                })
                .toList();

        order.setOrderItems(orderItems);

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        cartService.clearCart(cart);

        return toOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(User user, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findByUser(user, pageable)
                .map(this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrderById(User user, Long orderId) {
        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findAll(pageable)
                .map(this::toOrderResponse);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        order.setStatus(newStatus);
        return toOrderResponse(orderRepository.save(order));
    }

    @Transactional
    public void confirmPayment(String stripePaymentIntentId) {
        Order order = orderRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for payment: " + stripePaymentIntentId));
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    @Transactional
    public void linkPaymentIntent(Long orderId, String paymentIntentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStripePaymentIntentId(paymentIntentId);
        orderRepository.save(order);
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .orderItemId(item.getId())
                        .productName(item.getProductName())
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
