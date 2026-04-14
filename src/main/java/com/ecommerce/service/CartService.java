package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.CartItem;
import com.ecommerce.model.Product;
import com.ecommerce.model.User;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * CartService — manages the shopping cart.
 *
 * Design decisions:
 *   - Each user has exactly ONE persistent cart (created at registration)
 *   - Adding the same product twice → updates quantity (no duplicate rows)
 *   - Cart total is computed at read time (not stored) — always reflects current prices
 *   - Setting quantity to 0 via update → removes the item
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    /** Get the current user's cart with all items and computed total */
    @Transactional(readOnly = true)
    public CartResponse getCart(User user) {
        Cart cart = getOrCreateCart(user);
        return toCartResponse(cart);
    }

    /**
     * Add a product to cart, or increase its quantity if already present.
     * Stock validation: checks we're not adding more than available stock.
     */
    @Transactional
    public CartResponse addItem(User user, CartItemRequest request) {
        Cart cart = getOrCreateCart(user);
        Product product = productService.findProductEntityById(request.getProductId());

        // Check if this product is already in the cart
        Optional<CartItem> existingItem = cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItem.isPresent()) {
            // Product already in cart → update quantity
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.getQuantity();

            // Validate stock: can't add more than available
            validateStock(product, newQuantity);
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            // New product → create a new CartItem row
            validateStock(product, request.getQuantity());

            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getCartItems().add(newItem); // add to the in-memory list too (for toCartResponse)
            cartItemRepository.save(newItem);
        }

        return toCartResponse(cart);
    }

    /**
     * Update quantity of a specific cart item.
     * Quantity = 0 removes the item.
     */
    @Transactional
    public CartResponse updateItem(User user, Long cartItemId, Integer quantity) {
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        // Security check: make sure this item belongs to this user's cart
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("This item does not belong to your cart");
        }

        if (quantity <= 0) {
            // Remove item when quantity is set to 0
            cart.getCartItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            validateStock(item.getProduct(), quantity);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        // Reload cart to get fresh state after modification
        return toCartResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

    /** Remove a specific item from the cart */
    @Transactional
    public CartResponse removeItem(User user, Long cartItemId) {
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("This item does not belong to your cart");
        }

        cart.getCartItems().remove(item);
        cartItemRepository.delete(item);
        return toCartResponse(cart);
    }

    /** Clear all items from the cart (called after a successful order is placed) */
    @Transactional
    public void clearCart(Cart cart) {
        cart.getCartItems().clear();   // clearing the list + orphanRemoval = true → deletes all rows
        cartRepository.save(cart);
    }

    /**
     * Gets the user's cart, or creates one if it doesn't exist.
     * Cart should always exist (created at registration), but this is a safety net.
     */
    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder().user(user).build();
                    return cartRepository.save(newCart);
                });
    }

    /** Validates that we're not adding more items than available stock */
    private void validateStock(Product product, int requestedQuantity) {
        if (product.getStockQuantity() < requestedQuantity) {
            throw new BadRequestException(
                    "Insufficient stock. Available: " + product.getStockQuantity() +
                    ", Requested: " + requestedQuantity);
        }
    }

    /** Converts Cart entity to CartResponse DTO with computed totals */
    private CartResponse toCartResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getCartItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .imageUrl(item.getProduct().getImageUrl())
                        .unitPrice(item.getProduct().getPrice())
                        .quantity(item.getQuantity())
                        // Subtotal = price × quantity
                        .subtotal(item.getProduct().getPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        // Total = sum of all item subtotals
        BigDecimal totalAmount = itemResponses.stream()
                .map(CartResponse.CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .totalAmount(totalAmount)
                .totalItems(itemResponses.size())
                .build();
    }
}
