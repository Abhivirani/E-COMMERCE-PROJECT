package com.ecommerce.aiassistant.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stockQuantity,
        String imageUrl,
        Boolean active
) {
}
