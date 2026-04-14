package com.ecommerce.repository;

import com.ecommerce.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Only return active (not soft-deleted) products, with pagination
    Page<Product> findByActiveTrue(Pageable pageable);

    // Filter active products by category
    Page<Product> findByActiveTrueAndCategory(String category, Pageable pageable);

    // Search by name or description (case-insensitive) — uses JPQL (Java Persistence Query Language)
    // JPQL uses entity class names and field names, not table/column names
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

    // findById but only if active — prevents accessing soft-deleted products by ID
    Optional<Product> findByIdAndActiveTrue(Long id);
}
