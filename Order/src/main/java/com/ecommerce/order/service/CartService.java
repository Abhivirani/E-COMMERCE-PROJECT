package com.ecommerce.order.service;

import com.ecommerce.order.dto.CartItemRequest;
import com.ecommerce.order.dto.ProductResponse;
import com.ecommerce.order.exception.InvalidUserException;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.model.CartItem;

import com.ecommerce.order.repository.CartItemRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserValidationService userValidationService;
    private final ProductValidationService productValidationService;

    public boolean addToCart(String userId, CartItemRequest request) {

        if(!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid User");
        }

        // Product Validation
        if(!productValidationService.validateProduct(request.getProductId())) {
            throw new ProductNotFoundException("Invalid Product");
        }

        CartItem existingCartItem =
                cartItemRepository.findByUserIdAndProductId(
                        userId,
                        request.getProductId()
                );

        if(existingCartItem != null)
        {
            //update the quantity
            existingCartItem.setQuantity(existingCartItem.getQuantity()+ request.getQuantity());
            ProductResponse product =
                    productValidationService.getProduct(request.getProductId()).join();
            existingCartItem.setPrice(product.getPrice());
            cartItemRepository.save(existingCartItem);
        }else
        {
            //create new cart item
            ProductResponse product =
                    productValidationService.getProduct(request.getProductId()).join();
            CartItem cartItem = new CartItem();
            cartItem.setUserId(userId);
            cartItem.setProductId(request.getProductId());
            cartItem.setQuantity(request.getQuantity());
            cartItem.setPrice(product.getPrice());
            cartItemRepository.save(cartItem);
        }
        return true;
    }


    public boolean deleteItemFromCart(String userId, String productId) {
        if(!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid User");
        }

        // Product Validation
        if(!productValidationService.validateProduct(productId))
        {
            throw new ProductNotFoundException("Invalid Product");
        }

        CartItem cartItem =
                cartItemRepository.findByUserIdAndProductId(
                        userId,
                        productId
                );

        if(cartItem != null)
        {
            cartItemRepository.delete(cartItem);
            return true;
        }

        return false;
    }

    public List<CartItem> getCart(String userId)
    {
        if(!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid User");
        }

        return cartItemRepository.findByUserId(userId);
    }


    public void clearCart(String userId) {
        if(!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid User");
        }

        cartItemRepository.deleteByUserId(userId);
    }

    public boolean refreshCartItemPrice(String userId, String productId) {

        if (!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid User");
        }

        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(
                userId,
                productId
        );

        if (cartItem == null) {
            return false;
        }

        ProductResponse product =
                productValidationService.getProduct(productId).join();

        if (product == null || product.getPrice() == null) {
            throw new IllegalStateException(
                    "Product price is unavailable for product: " + productId
            );
        }

        cartItem.setPrice(product.getPrice());

        cartItemRepository.save(cartItem);

        return true;
    }
}
