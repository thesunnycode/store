package com.ecommerce.repository;

import com.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository — database access for User entities.
 *
 * JpaRepository<User, Long> gives us free methods:
 *   save(), findById(), findAll(), deleteById(), count(), existsById()...
 * We add custom query methods by following Spring Data's method naming convention.
 * Spring Data reads the method name and generates the SQL automatically.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data generates: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // Spring Data generates: SELECT COUNT(*) FROM users WHERE email = ? > 0
    boolean existsByEmail(String email);
}
