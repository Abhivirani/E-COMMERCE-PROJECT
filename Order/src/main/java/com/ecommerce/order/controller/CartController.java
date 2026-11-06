package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CartItemRequest;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> addToCart(Authentication authentication,
                                            @RequestBody CartItemRequest request){
        String userId = authentication.getName();
        if(!cartService.addToCart(userId,request))
        {
            return ResponseEntity.badRequest().body("Product Out Of Stock or User not found");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> removeFromCart(
            Authentication authentication,
            @PathVariable String productId) {
        String userId = authentication.getName();
        boolean deleted = cartService.deleteItemFromCart(userId, productId);
        return deleted ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CartItem>> getCart(
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PutMapping("/items/{productId}/refresh-price")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> refreshCartItemPrice(
            Authentication authentication,
            @PathVariable String productId) {

        String userId = authentication.getName();

        boolean updated =
                cartService.refreshCartItemPrice(userId, productId);

        if (!updated) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok("Cart item price refreshed successfully");
    }
}
