package com.ecommerce.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ProductResponse — what we return to the client for product data.
 *
 * Why not return the Product entity directly?
 * 1. Security: entity has internal fields (active flag, timestamps) we may not want to expose
 * 2. Flexibility: we can add computed fields (e.g., inStock) without changing the DB schema
 * 3. Decoupling: API contract stays stable even if internal entity changes
 */
@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String category;
    private String imageUrl;
    private boolean inStock;             // computed: stockQuantity > 0
    private LocalDateTime createdAt;
}
