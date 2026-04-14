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

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public CartResponse getCart(User user) {
        Cart cart = getOrCreateCart(user);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(User user, CartItemRequest request) {
        Cart cart = getOrCreateCart(user);
        Product product = productService.findProductEntityById(request.getProductId());

        Optional<CartItem> existingItem = cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.getQuantity();

            validateStock(product, newQuantity);
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            validateStock(product, request.getQuantity());

            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getCartItems().add(newItem);
            cartItemRepository.save(newItem);
        }

        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(User user, Long cartItemId, Integer quantity) {
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("This item does not belong to your cart");
        }

        if (quantity <= 0) {
            cart.getCartItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            validateStock(item.getProduct(), quantity);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return toCartResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

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

    @Transactional
    public void clearCart(Cart cart) {
        cart.getCartItems().clear();
        cartRepository.save(cart);
    }

    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder().user(user).build();
                    return cartRepository.save(newCart);
                });
    }

    private void validateStock(Product product, int requestedQuantity) {
        if (product.getStockQuantity() < requestedQuantity) {
            throw new BadRequestException(
                    "Insufficient stock. Available: " + product.getStockQuantity() +
                    ", Requested: " + requestedQuantity);
        }
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getCartItems().stream()
                .map(item -> CartResponse.CartItemResponse.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .imageUrl(item.getProduct().getImageUrl())
                        .unitPrice(item.getProduct().getPrice())
                        .quantity(item.getQuantity())

                        .subtotal(item.getProduct().getPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

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
