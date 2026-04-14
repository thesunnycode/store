package com.ecommerce.service;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Product;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProductService — handles all product operations.
 *
 * Pagination is used throughout because a real e-commerce app could have
 * thousands of products. Loading all products at once would crash the DB.
 * Page<T> from Spring Data returns a chunk of results with total count metadata.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /** Returns all active products, sorted by creation date (newest first), paginated */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(int page, int size) {
        // PageRequest.of(page, size, sort) → constructs the Pageable object
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return productRepository.findByActiveTrue(pageable)
                .map(this::toProductResponse); // convert each Product entity to DTO
    }

    /** Filter products by category */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return productRepository.findByActiveTrueAndCategory(category, pageable)
                .map(this::toProductResponse);
    }

    /** Full-text search across name and description */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.searchProducts(query, pageable)
                .map(this::toProductResponse);
    }

    /** Get a single product by ID (only active) */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return toProductResponse(product);
    }

    /** Admin: create a new product */
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .active(true)
                .build();

        return toProductResponse(productRepository.save(product));
    }

    /** Admin: update an existing product */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        // fetch product or throw 404
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Update only the fields provided — this is a full update (PUT semantics)
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(request.getCategory());
        product.setImageUrl(request.getImageUrl());

        // @PreUpdate on entity automatically updates updatedAt before saving
        return toProductResponse(productRepository.save(product));
    }

    /**
     * Admin: soft delete a product.
     * Sets active = false instead of deleting the DB row.
     * Why soft delete?
     *   - Order history references products by ID — hard delete would break history
     *   - Easier to recover accidentally deleted products
     *   - Maintains referential integrity
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setActive(false);          // soft delete
        productRepository.save(product);
    }

    /** Converts Product entity to ProductResponse DTO */
    private ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .inStock(product.getStockQuantity() > 0) // computed field
                .createdAt(product.getCreatedAt())
                .build();
    }

    /**
     * Package-private helper used by CartService and OrderService
     * to load a Product entity (not DTO) by ID.
     */
    Product findProductEntityById(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }
}
