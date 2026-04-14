package com.ecommerce.controller;

import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.User;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @PostMapping("/create-intent")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentIntent(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        String currency = body.getOrDefault("currency", "inr").toString();

        var order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new BadRequestException("Order not found or doesn't belong to you"));

        try {
            Map<String, String> paymentData = paymentService.createPaymentIntent(
                    orderId, order.getTotalAmount(), currency);
            return ResponseEntity.ok(ApiResponse.success("PaymentIntent created", paymentData));
        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent: {}", e.getMessage());
            throw new BadRequestException("Payment processing failed: " + e.getMessage());
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            paymentService.handleWebhook(payload, sigHeader);

            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage());

            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
    }
}
