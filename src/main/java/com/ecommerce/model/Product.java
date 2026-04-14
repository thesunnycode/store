package com.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product entity — maps to the "products" table.
 *
 * BigDecimal is used for price instead of double/float because floating-point
 * types can produce rounding errors in financial calculations (e.g., 0.1 + 0.2 ≠ 0.3).
 * BigDecimal is exact and safe for money.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT") // TEXT type allows descriptions longer than VARCHAR(255)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2) // stores up to 99999999.99
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;         // how many units are in inventory

    @Column(nullable = false)
    private String category;

    private String imageUrl;               // URL to product image (e.g., Cloudinary/S3 link)

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;         // soft delete: false = hidden from catalog, not deleted

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate                             // JPA lifecycle hook — called before every UPDATE
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
