package com.ecommerce.controller;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.dto.response.ApiResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ProductController — manages the product catalog.
 *
 * PUBLIC endpoints (no auth needed):
 *   GET /api/products              → paginated list of all active products
 *   GET /api/products/{id}         → single product details
 *   GET /api/products/search       → search by name/description
 *   GET /api/products/category     → filter by category
 *
 * ADMIN-only endpoints (requires ADMIN role):
 *   POST   /api/products           → create new product
 *   PUT    /api/products/{id}      → update existing product
 *   DELETE /api/products/{id}      → soft-delete product
 *
 * @PreAuthorize("hasRole('ADMIN')") → Spring Security checks the role BEFORE
 * the method runs. Requires @EnableMethodSecurity in SecurityConfig.
 * Throws AccessDeniedException if user doesn't have ADMIN role → GlobalExceptionHandler → 403.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /api/products?page=0&size=10
     * Returns a paginated list of all active products.
     * @RequestParam with defaultValue → query param is optional, defaults applied if omitted
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ProductResponse> products = productService.getAllProducts(page, size);
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", products));
    }

    /**
     * GET /api/products/{id}
     * Returns a single product by its ID.
     * @PathVariable → extracts the {id} segment from the URL.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", product));
    }

    /**
     * GET /api/products/search?query=laptop&page=0&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ProductResponse> products = productService.searchProducts(query, page, size);
        return ResponseEntity.ok(ApiResponse.success("Search results", products));
    }

    /**
     * GET /api/products/category?name=Electronics&page=0&size=10
     */
    @GetMapping("/category")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getByCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ProductResponse> products = productService.getProductsByCategory(name, page, size);
        return ResponseEntity.ok(ApiResponse.success("Products by category", products));
    }

    /**
     * POST /api/products — ADMIN only
     * Creates a new product. Returns 201 Created.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    /**
     * PUT /api/products/{id} — ADMIN only
     * Full update of an existing product.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    /**
     * DELETE /api/products/{id} — ADMIN only
     * Soft-deletes a product (sets active = false).
     * Returns 200 with no data payload.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }
}
