package com.ecommerce.repository;

import com.ecommerce.model.Order;
import com.ecommerce.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Customer: see only their own orders, paginated
    Page<Order> findByUser(User user, Pageable pageable);

    // Used when confirming a Stripe payment — look up order by Stripe's payment ID
    Optional<Order> findByStripePaymentIntentId(String stripePaymentIntentId);

    // Verify an order belongs to this user (prevents one customer viewing another's order)
    Optional<Order> findByIdAndUser(Long id, User user);
}
