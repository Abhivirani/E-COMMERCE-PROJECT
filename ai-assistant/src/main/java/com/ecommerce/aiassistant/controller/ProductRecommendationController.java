
package com.ecommerce.aiassistant.controller;

import com.ecommerce.aiassistant.dto.ProductResponse;
import com.ecommerce.aiassistant.service.ProductSearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/ai")
public class ProductRecommendationController {

    private final ChatClient chatClient;
    private final ProductSearchService productSearchService;

    public ProductRecommendationController(
            ChatClient.Builder chatClientBuilder,
            ProductSearchService productSearchService) {

        this.chatClient = chatClientBuilder.build();
        this.productSearchService = productSearchService;
    }

    @PostMapping("/recommend")
    public RecommendationResponse recommendProducts(
            @RequestBody RecommendationRequest request) {

        // 1. Fetch active and in-stock products
        List<ProductResponse> products =
                productSearchService.getAllProducts()
                        .stream()
                        .filter(ProductResponse::active)
                        .filter(product -> product.stockQuantity() > 0)
                        .toList();

        if (products.isEmpty()) {
            return new RecommendationResponse(
                    "No products are currently available.",
                    List.of()
            );
        }

        // 2. Create product map using product ID
        Map<Long, ProductResponse> productMap =
                products.stream()
                        .collect(Collectors.toMap(
                                ProductResponse::id,
                                Function.identity()
                        ));

        // 3. Extract budget from user's request
        BigDecimal budget = extractBudget(request.keyword());

        // 4. Ask AI for product recommendations
        AiRecommendationResponse aiResponse =
                chatClient.prompt()

                        .system("""
                                You are ShopMate, an intelligent AI shopping assistant
                                for an e-commerce platform.
                        
                                Your task is to understand the user's shopping intent
                                and recommend the most relevant products.
                        
                                STRICT RULES:
                        
                                1. Recommend products ONLY from the provided catalog.
                                2. Return only product IDs and recommendation reasons.
                                3. Never invent product IDs or product details.
                                4. Respect the user's budget.
                                5. Recommend only active products with available stock.
                                6. If no product matches, return an empty list.
                                7. Be friendly and concise.
                                8. Relevance is more important than recommendation quantity.
                                9. Do not recommend unrelated products just because
                                   they are within budget.
                                10. Understand the user's purpose, audience, and context.
                                11. For gift requests, prioritize products suitable
                                    for the specified recipient or occasion.
                                12. For sports requests, recommend relevant sports
                                    equipment and sports-related products.
                                13. For work or study requests, prioritize products
                                    useful for work or studying.
                                14. Recommend a maximum of 3 products.
                                15. Each recommendation reason must be based on
                                    actual catalog information.
                        
                                Return the structured response.
                                """)

                        .user("""
                                Customer shopping request:
                                %s
                        
                                Customer budget:
                                %s
                        
                                Product catalog:
                                %s
                        
                                Analyze the customer's intent carefully.
                                Select the most relevant products.
                                Return no more than 3 recommendations.
                                """.formatted(
                                                        request.keyword(),
                                                        budget == null ? "Not specified" : "₹" + budget,
                                                        products
                                                ))
                        .call()
                        .entity(AiRecommendationResponse.class);

        // 5. Handle empty AI response
        if (aiResponse == null || aiResponse.recommendations() == null) {
            return new RecommendationResponse(
                    "Sorry, I could not find suitable recommendations.",
                    List.of()
            );
        }

        // 6. Map AI recommendations to actual catalog products
        List<RecommendedProduct> recommendations =
                aiResponse.recommendations()
                        .stream()
                        .filter(item -> item.productId() != null)
                        .filter(item -> productMap.containsKey(item.productId()))
                        .map(item -> {
                            ProductResponse product =
                                    productMap.get(item.productId());

                            return new RecommendedProduct(
                                    product.id(),
                                    product.name(),
                                    product.price(),
                                    product.imageUrl(),
                                    product.stockQuantity(),
                                    item.reason()
                            );
                        })
                        .toList();

        // 7. Server-side budget validation
        if (budget != null) {
            recommendations = recommendations.stream()
                    .filter(product ->
                            product.price() != null
                                    && product.price().compareTo(budget) <= 0)
                    .toList();
        }

        // 8. Prepare final response
        String message = recommendations.isEmpty()
                ? "Sorry, no matching products are available within your requirements."
                : "Here are your personalized product recommendations.";

        return new RecommendationResponse(message, recommendations);
    }

    // Extract budget from natural-language request
    private BigDecimal extractBudget(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                "(?i)(under|below|less than|within|up to|upto|budget of)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+)"
        );

        Matcher matcher = pattern.matcher(keyword);

        if (matcher.find()) {
            String amount = matcher.group(2).replace(",", "");

            return new BigDecimal(amount);
        }

        return null;
    }


    @PostMapping("/search")
    public List<ProductResponse> searchProducts(
            @RequestBody NaturalLanguageSearchRequest request,
            @RequestHeader("Authorization") String authorization) {

        System.out.println("AI Search endpoint called");
        System.out.println("Query: " + request.query());

        System.out.println("Authorization present: "
                + (authorization != null));

        List<ProductResponse> products =
                productSearchService.searchProductsByNaturalLanguage(
                        request.query(),
                        authorization);

        System.out.println("Products returned: "
                + (products == null ? "null" : products.size()));

        System.out.println("Products: " + products);

        return products;
    }

    public record NaturalLanguageSearchRequest(String query) {
    }

    // Request DTO
    public record RecommendationRequest(String keyword) {
    }

    // AI response DTO
    public record AiRecommendationResponse(
            List<AiProductSuggestion> recommendations) {
    }

    public record AiProductSuggestion(
            Long productId,
            String reason) {
    }

    // Final product response DTO
    public record RecommendedProduct(
            Long id,
            String name,
            BigDecimal price,
            String imageUrl,
            Integer stockQuantity,
            String reason) {
    }

    // API response DTO
    public record RecommendationResponse(
            String message,
            List<RecommendedProduct> recommendations) {
    }
}
