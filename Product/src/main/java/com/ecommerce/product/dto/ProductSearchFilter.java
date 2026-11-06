
package com.ecommerce.product.dto;

import java.math.BigDecimal;

public record ProductSearchFilter(
        String keyword,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock,
        Boolean active
) {
}
