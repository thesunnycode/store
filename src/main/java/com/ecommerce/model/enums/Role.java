package com.ecommerce.model.enums;

/**
 * Defines the two roles in this system.
 *
 * Spring Security uses these as "authorities" to decide what each user can access.
 * ADMIN → can create/update/delete products, view all orders
 * CUSTOMER → can browse products, manage their cart, place orders
 */
public enum Role {
    ADMIN,
    CUSTOMER
}
