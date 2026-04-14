package com.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Cart entity — each user has exactly one persistent cart.
 *
 * The cart is never deleted; it's emptied when an order is placed.
 * This is better UX than creating a new cart each session.
 *
 * CascadeType.ALL → any operation on Cart (save, delete) cascades to CartItems.
 * orphanRemoval = true → if a CartItem is removed from the list, it's deleted from DB.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One-to-One: each user has one cart.
     * @JoinColumn creates a "user_id" foreign key column in the "carts" table.
     */
    @OneToOne(fetch = FetchType.LAZY)      // LAZY = don't load User from DB unless accessed
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * One cart has many items.
     * mappedBy = "cart" → the "cart" field in CartItem owns this relationship.
     * Initialized to empty list to avoid NullPointerException when adding items.
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> cartItems = new ArrayList<>();
}
