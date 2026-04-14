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

/**
 * PaymentController — handles Stripe payment flows.
 *
 * Endpoints:
 *   POST /api/payments/create-intent      → creates a Stripe PaymentIntent, returns clientSecret
 *   POST /api/payments/webhook            → receives Stripe webhook events (PUBLIC, no auth)
 *
 * PAYMENT FLOW (explain this in interviews):
 *   1. Customer places order → order created with status PENDING
 *   2. Frontend calls /create-intent with the orderId
 *   3. We create a PaymentIntent in Stripe → get back a clientSecret
 *   4. Frontend uses Stripe.js + clientSecret to collect card details securely
 *   5. Stripe processes payment → sends webhook to /webhook
 *   6. We update order status to CONFIRMED
 *
 * Why this flow?
 *   Card details NEVER reach our server → we are NOT PCI compliant scope
 *   Stripe handles 3DS, fraud detection, retry logic
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    /**
     * POST /api/payments/create-intent
     * Body: { "orderId": 1, "currency": "inr" }
     *
     * Returns the Stripe clientSecret which the frontend needs to confirm payment.
     * Requires authentication — only the order owner can initiate payment.
     */
    @PostMapping("/create-intent")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPaymentIntent(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {

        Long orderId = Long.valueOf(body.get("orderId").toString());
        String currency = body.getOrDefault("currency", "inr").toString();

        // Load the order to get the total amount
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

    /**
     * POST /api/payments/webhook
     *
     * This endpoint is called by Stripe, NOT by the user's browser.
     * It must be PUBLIC (no JWT required) — configured in SecurityConfig.
     *
     * The raw request body must be passed AS-IS to Stripe for signature verification.
     * That's why we use @RequestBody String payload — not a DTO.
     * If Spring parsed it as JSON first, the byte order could change and signature verification would fail.
     *
     * Stripe-Signature header contains the HMAC signature of the payload.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            paymentService.handleWebhook(payload, sigHeader);
            // Stripe expects a 200 OK response — if we return anything else, it retries the webhook
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage());
            // Return 400 to tell Stripe this webhook failed (it will retry)
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
    }
}
