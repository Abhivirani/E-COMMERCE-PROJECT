package com.ecommerce.order.dto;

import lombok.Data;

@Data
public class CartItemRequest {
    public String productId;
    public Integer quantity;
}
