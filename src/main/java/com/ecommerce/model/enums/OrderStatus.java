package com.ecommerce.model.enums;

/**
 * Lifecycle states of an order.
 *
 * PENDING      → order placed, awaiting payment confirmation
 * CONFIRMED    → payment successful, order confirmed
 * SHIPPED      → package handed to delivery carrier
 * DELIVERED    → package received by customer
 * CANCELLED    → order cancelled before shipment
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
