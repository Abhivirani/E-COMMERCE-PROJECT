
package com.ecommerce.aiassistant.service;

import com.ecommerce.aiassistant.dto.ProductResponse;
import com.ecommerce.aiassistant.dto.ProductSearchFilter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class ProductSearchService {

    private final ProductServiceClient productServiceClient;
    private final ChatClient chatClient;

    private static final Logger log =
            LoggerFactory.getLogger(ProductSearchService.class);

    public ProductSearchService(
            ProductServiceClient productServiceClient,
            ChatClient.Builder chatClientBuilder) {

        this.productServiceClient = productServiceClient;
        this.chatClient = chatClientBuilder.build();
    }

    // Existing keyword search
    public List<ProductResponse> searchProducts(String keyword) {
        return productServiceClient.searchProducts(keyword);
    }

    // Existing get all products
    public List<ProductResponse> getAllProducts() {
        return productServiceClient.getAllProducts();
    }

    // Natural language product search
    public List<ProductResponse> searchProductsByNaturalLanguage(
            String userQuery,
            String authorization) {

        // 1. Validate user input
        if (userQuery == null || userQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "Search query cannot be empty");
        }

        String query = userQuery.trim();

        if (query.length() > 200) {
            throw new IllegalArgumentException(
                    "Search query cannot exceed 200 characters");
        }

        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new IllegalArgumentException(
                    "A valid Bearer token is required");
        }

        log.info("Received AI search query: {}", query);

        // 2. Generate filter using AI
        ProductSearchFilter generatedFilter;

        try {
            generatedFilter = chatClient.prompt()
                    .system("""
                            You are an intelligent e-commerce product search
                            filter generator.

                            Convert the user's query into a ProductSearchFilter.

                            Fields:
                            - keyword: String
                            - category: String
                            - minPrice: BigDecimal
                            - maxPrice: BigDecimal
                            - inStock: Boolean
                            - active: Boolean

                            RULES:
                            1. Extract product names into keyword.
                            2. Extract product categories into category.
                            3. Do not duplicate category words in keyword.
                            4. Category-only queries must have keyword=null.
                            5. Under, below, less than means maxPrice.
                            6. Above, more than means minPrice.
                            7. In stock means inStock=true.
                            8. Out of stock means inStock=false.
                            9. Return null for unspecified filters.
                            10. Do not set minPrice to zero unless specified.
                            11. Set active=true.
                            12. Do not invent filters.
                            13. Return only the structured filter object.
                            """)
                    .user(query)
                    .call()
                    .entity(ProductSearchFilter.class);

        } catch (Exception exception) {
            log.error("AI filter generation failed: {}",
                    exception.getClass().getSimpleName());

            throw new IllegalStateException(
                    "AI product search is temporarily unavailable");
        }

        if (generatedFilter == null) {
            throw new IllegalStateException(
                    "AI could not generate product search filters");
        }

        log.info("AI generated filter: {}", generatedFilter);

        // 3. Correct category/keyword conflicts
        ProductSearchFilter filter =
                correctFilter(generatedFilter, query);

        // 4. Validate generated filter
        validateFilter(filter);

        log.info("Validated filter: {}", filter);

        // 5. Call Product Service
        List<ProductResponse> products =
                productServiceClient.searchProductsByFilter(
                        filter,
                        authorization);

        if (products == null) {
            log.warn("Product Service returned null response");
            return List.of();
        }

        log.info("Product Service returned {} products",
                products.size());

        return products;
    }

    // Validate AI-generated filters
    private void validateFilter(ProductSearchFilter filter) {

        if (filter.minPrice() != null
                && filter.minPrice().signum() < 0) {
            throw new IllegalArgumentException(
                    "Minimum price cannot be negative");
        }

        if (filter.maxPrice() != null
                && filter.maxPrice().signum() < 0) {
            throw new IllegalArgumentException(
                    "Maximum price cannot be negative");
        }

        if (filter.minPrice() != null
                && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new IllegalArgumentException(
                    "Minimum price cannot exceed maximum price");
        }

        if (filter.keyword() != null
                && filter.keyword().length() > 100) {
            throw new IllegalArgumentException(
                    "Search keyword is too long");
        }

        if (filter.category() != null
                && filter.category().length() > 50) {
            throw new IllegalArgumentException(
                    "Product category is too long");
        }
    }


    // Correct category and keyword conflicts
    private ProductSearchFilter correctFilter(
            ProductSearchFilter filter,
            String userQuery) {

        String category = filter.category();
        String keyword = filter.keyword();

        String normalizedQuery = userQuery.toLowerCase();

        // Deterministic category extraction for Sports
        if (normalizedQuery.matches(".*\\bsports?\\b.*")) {
            category = "Sports";
        }

        // Remove duplicated category words from keyword
        if (keyword != null && !keyword.isBlank()) {

            String normalizedKeyword = keyword
                    .replaceAll("(?i)\\bsports?\\b", "")
                    .replaceAll("(?i)\\bproducts?\\b", "")
                    .trim();

            // Remove extra spaces
            normalizedKeyword = normalizedKeyword
                    .replaceAll("\\s+", " ")
                    .trim();

            keyword = normalizedKeyword.isBlank()
                    ? null
                    : normalizedKeyword;
        }

        return new ProductSearchFilter(
                keyword,
                category,
                filter.minPrice(),
                filter.maxPrice(),
                filter.inStock(),
                filter.active() == null ? true : filter.active()
        );
    }
}
