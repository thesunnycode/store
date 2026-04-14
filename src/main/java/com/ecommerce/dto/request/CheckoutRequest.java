package com.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** DTO for POST /api/orders/checkout */
@Data
public class CheckoutRequest {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;
}
