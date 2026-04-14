package com.ecommerce.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * StripeConfig — initializes the Stripe Java SDK with our secret API key.
 *
 * Stripe.apiKey is a static field on the Stripe class.
 * Setting it once here makes it available globally to all Stripe API calls.
 *
 * @PostConstruct runs this method automatically after Spring creates this bean
 * and injects all @Value fields — so the key is guaranteed to be loaded from
 * application.properties before we set it.
 */
@Configuration
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @PostConstruct
    public void initStripe() {
        Stripe.apiKey = stripeSecretKey;
    }
}
