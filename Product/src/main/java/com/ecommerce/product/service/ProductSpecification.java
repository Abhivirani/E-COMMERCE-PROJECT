
package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

public class ProductSpecification {

    public static Specification<Product> buildSpecification(
            String keyword,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Boolean active
    ) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Keyword filter: search in product name and description
            if (keyword != null && !keyword.isBlank()) {

                String searchKeyword = "%" + keyword.toLowerCase() + "%";

                Predicate namePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")),
                        searchKeyword
                );

                Predicate descriptionPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("description")),
                        searchKeyword
                );

                predicates.add(
                        criteriaBuilder.or(
                                namePredicate,
                                descriptionPredicate
                        )
                );
            }

            // Category filter
            if (category != null && !category.isBlank()) {
                predicates.add(
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(root.get("category")),
                                category.toLowerCase()
                        )
                );
            }

            // Minimum price filter
            if (minPrice != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("price"),
                                minPrice
                        )
                );
            }

            // Maximum price filter
            if (maxPrice != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("price"),
                                maxPrice
                        )
                );
            }

            // Stock availability filter
            if (inStock != null) {

                if (inStock) {
                    predicates.add(
                            criteriaBuilder.greaterThan(
                                    root.get("stockQuantity"),
                                    0
                            )
                    );
                } else {
                    predicates.add(
                            criteriaBuilder.or(
                                    criteriaBuilder.lessThanOrEqualTo(
                                            root.get("stockQuantity"),
                                            0
                                    ),
                                    criteriaBuilder.isNull(
                                            root.get("stockQuantity")
                                    )
                            )
                    );
                }
            }

            // Active status filter
            if (active != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("active"),
                                active
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}
