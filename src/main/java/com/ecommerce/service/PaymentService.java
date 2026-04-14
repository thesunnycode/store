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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderService orderService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public Map<String, String> createPaymentIntent(Long orderId, BigDecimal amount, String currency)
            throws StripeException {
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.toLowerCase())
                .addPaymentMethodType("card")
                .putMetadata("orderId", orderId.toString())
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        orderService.linkPaymentIntent(orderId, paymentIntent.getId());

        log.info("Created PaymentIntent {} for order {}", paymentIntent.getId(), orderId);

        Map<String, String> response = new HashMap<>();
        response.put("clientSecret", paymentIntent.getClientSecret());
        response.put("paymentIntentId", paymentIntent.getId());
        return response;
    }

    public void handleWebhook(String payload, String sigHeader) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new RuntimeException("Invalid webhook signature");
        }

        log.info("Received Stripe webhook event: {}", event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                if (deserializer.getObject().isPresent()) {
                    PaymentIntent paymentIntent = (PaymentIntent) deserializer.getObject().get();
                    log.info("Payment succeeded for PaymentIntent: {}", paymentIntent.getId());
                    orderService.confirmPayment(paymentIntent.getId());
                }
            }

            case "payment_intent.payment_failed" -> {
                log.warn("Payment failed for event: {}", event.getId());
            }

            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }
}
