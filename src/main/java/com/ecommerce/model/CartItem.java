package com.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * CartItem entity — a single line item inside a cart.
 *
 * Represents: "User X has 3 units of Product Y in their cart."
 * The price is NOT stored here — we always fetch the current price from Product
 * at checkout time (so price changes are reflected).
 */
@Entity
@Table(name = "cart_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
// ^ unique constraint: same product cannot appear twice in the same cart (we update quantity instead)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Many cart items belong to one cart.
     * FetchType.LAZY = don't load Cart from DB unless cart field is accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;
}
