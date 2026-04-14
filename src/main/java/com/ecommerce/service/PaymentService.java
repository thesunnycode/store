package com.ecommerce.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * PaymentService — handles all Stripe payment operations.
 *
 * HOW STRIPE PAYMENTS WORK (important for interviews):
 *
 *   1. Frontend calls POST /api/payments/create-intent with the order ID
 *   2. We create a Stripe PaymentIntent (server-side) — this returns a clientSecret
 *   3. Frontend uses the clientSecret with Stripe.js to collect card details
 *      (card details NEVER touch our server — PCI compliance)
 *   4. Stripe processes the payment and sends a webhook to /api/payments/webhook
 *   5. Our webhook handler confirms the order when payment succeeds
 *
 * Why PaymentIntent instead of direct charge?
 *   PaymentIntent supports 3D Secure, multiple payment methods, and retry logic.
 *   It's the modern Stripe approach (Charges API is legacy).
 *
 * Amounts in Stripe are in the SMALLEST currency unit (paise for INR, cents for USD).
 * ₹500 = 50000 paise. We multiply by 100.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderService orderService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;        // used to verify Stripe webhook authenticity

    /**
     * Creates a Stripe PaymentIntent for the given order.
     * Returns the clientSecret which the frontend uses to confirm payment.
     *
     * @param orderId     the order being paid for
     * @param amount      total amount in INR (e.g., 500.00)
     * @param currency    currency code (e.g., "inr", "usd")
     * @return clientSecret string for the frontend
     */
    public Map<String, String> createPaymentIntent(Long orderId, BigDecimal amount, String currency)
            throws StripeException {

        // Convert amount to smallest unit (paise for INR)
        // BigDecimal.multiply(100).longValue() → 500.00 → 50000
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.toLowerCase())       // Stripe requires lowercase
                .addPaymentMethodType("card")
                .putMetadata("orderId", orderId.toString()) // attach our order ID to Stripe record
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        // Store the PaymentIntent ID on the order (for webhook matching later)
        orderService.linkPaymentIntent(orderId, paymentIntent.getId());

        log.info("Created PaymentIntent {} for order {}", paymentIntent.getId(), orderId);

        Map<String, String> response = new HashMap<>();
        response.put("clientSecret", paymentIntent.getClientSecret()); // frontend needs this
        response.put("paymentIntentId", paymentIntent.getId());
        return response;
    }

    /**
     * Processes Stripe webhook events.
     *
     * WHY VERIFY THE SIGNATURE?
     * Anyone on the internet could POST fake events to our webhook URL.
     * Stripe signs each event with our webhook secret. We verify this signature
     * to ensure the event genuinely came from Stripe, not a malicious actor.
     *
     * @param payload    raw request body as string (must be raw — not parsed)
     * @param sigHeader  value of the "Stripe-Signature" HTTP header
     */
    public void handleWebhook(String payload, String sigHeader) {
        Event event;

        try {
            // Stripe verifies the signature and parses the event in one step
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // Signature mismatch → reject the request (could be a spoofed event)
            log.error("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new RuntimeException("Invalid webhook signature");
        }

        log.info("Received Stripe webhook event: {}", event.getType());

        // Handle the specific event types we care about
        switch (event.getType()) {

            case "payment_intent.succeeded" -> {
                // Payment was successful — confirm the order
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                if (deserializer.getObject().isPresent()) {
                    PaymentIntent paymentIntent = (PaymentIntent) deserializer.getObject().get();
                    log.info("Payment succeeded for PaymentIntent: {}", paymentIntent.getId());
                    orderService.confirmPayment(paymentIntent.getId());
                }
            }

            case "payment_intent.payment_failed" -> {
                // Payment failed — log it (could notify customer via email in a real app)
                log.warn("Payment failed for event: {}", event.getId());
                // In a production app: update order to FAILED status, notify customer
            }

            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }
}
