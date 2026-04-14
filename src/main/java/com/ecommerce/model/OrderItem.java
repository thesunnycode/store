package com.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * OrderItem — a line item within a placed order.
 *
 * KEY DESIGN DECISION: priceAtPurchase is stored separately from Product.price.
 * Reason: product prices can change. The order record must reflect what the
 * customer actually paid, not the current price. This is a snapshot pattern.
 *
 * productName is also stored separately for the same reason — product might
 * be renamed or deleted later, but the order history must remain accurate.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")       // nullable — product might be deleted, order history preserved
    private Product product;

    @Column(nullable = false)
    private String productName;            // snapshot of name at purchase time

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;    // snapshot of price at purchase time

    /**
     * Convenience method: total cost for this line item.
     * e.g., 3 units × ₹500 = ₹1500
     */
    public BigDecimal getSubtotal() {
        return priceAtPurchase.multiply(BigDecimal.valueOf(quantity));
    }
}
