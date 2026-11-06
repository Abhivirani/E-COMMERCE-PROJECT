
package com.ecommerce.aiassistant.service;

import com.ecommerce.aiassistant.dto.ProductResponse;
import com.ecommerce.aiassistant.dto.ProductSearchFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductSearchServiceTest {

    private ProductServiceClient productServiceClient;
    private ChatClient chatClient;
    private ChatClient.Builder chatClientBuilder;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    private ProductSearchService productSearchService;

    @BeforeEach
    void setUp() {
        productServiceClient = mock(ProductServiceClient.class);
        chatClient = mock(ChatClient.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        productSearchService =
                new ProductSearchService(
                        productServiceClient,
                        chatClientBuilder);
    }

    @Test
    void shouldRejectEmptyQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                "   ",
                                "Bearer test-token"));

        verifyNoInteractions(chatClient);
        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldRejectMissingAuthorization() {
        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                "Find sports products",
                                null));

        verifyNoInteractions(chatClient);
        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldRejectQueryLongerThan200Characters() {
        String query = "a".repeat(201);

        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                query,
                                "Bearer test-token"));

        verifyNoInteractions(chatClient);
        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldRejectNullFilterGeneratedByAI() {
        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                "Find sports products",
                                "Bearer test-token"));

        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldCorrectSportsCategoryAndDefaultActive() {
        ProductSearchFilter generatedFilter =
                new ProductSearchFilter(
                        "sports products football",
                        null,
                        null,
                        new BigDecimal("1000"),
                        true,
                        null);

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        when(productServiceClient.searchProductsByFilter(
                any(ProductSearchFilter.class),
                anyString()))
                .thenReturn(List.of());

        List<ProductResponse> result =
                productSearchService.searchProductsByNaturalLanguage(
                        "Find sports products under 1000 that are in stock",
                        "Bearer test-token");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(productServiceClient).searchProductsByFilter(
                argThat(filter ->
                        "football".equals(filter.keyword())
                                && "Sports".equals(filter.category())
                                && filter.maxPrice()
                                .compareTo(new BigDecimal("1000")) == 0
                                && Boolean.TRUE.equals(filter.inStock())
                                && Boolean.TRUE.equals(filter.active())),
                eq("Bearer test-token"));
    }

    @Test
    void shouldRejectNegativeMinimumPrice() {
        ProductSearchFilter generatedFilter =
                new ProductSearchFilter(
                        "football",
                        "Sports",
                        new BigDecimal("-100"),
                        new BigDecimal("1000"),
                        true,
                        true);

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                "Find football under 1000",
                                "Bearer test-token"));

        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldRejectNegativeMaximumPrice() {
        ProductSearchFilter generatedFilter =
                new ProductSearchFilter(
                        "football",
                        "Sports",
                        null,
                        new BigDecimal("-500"),
                        true,
                        true);

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                "Find football",
                                "Bearer test-token"));

        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldRejectMinimumPriceGreaterThanMaximumPrice() {
        ProductSearchFilter generatedFilter =
                new ProductSearchFilter(
                        "football",
                        "Sports",
                        new BigDecimal("2000"),
                        new BigDecimal("1000"),
                        true,
                        true);

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService
                        .searchProductsByNaturalLanguage(
                                "Find football between 1000 and 2000",
                                "Bearer test-token"));

        verifyNoInteractions(productServiceClient);
    }

    @Test
    void shouldRejectKeywordLongerThan100Characters() {
        String longKeyword = "a".repeat(101);

        ProductSearchFilter generatedFilter = new ProductSearchFilter(
                longKeyword,
                null,
                null,
                null,
                true,
                true
        );

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService.searchProductsByNaturalLanguage(
                        "Find products",
                        "Bearer test-token"
                )
        );

        verifyNoInteractions(productServiceClient);
    }
    @Test
    void shouldRejectCategoryLongerThan50Characters() {
        String longCategory = "a".repeat(51);

        ProductSearchFilter generatedFilter = new ProductSearchFilter(
                "football",
                longCategory,
                null,
                null,
                true,
                true
        );

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        assertThrows(
                IllegalArgumentException.class,
                () -> productSearchService.searchProductsByNaturalLanguage(
                        "Find football",
                        "Bearer test-token"
                )
        );

        verifyNoInteractions(productServiceClient);
    }
    @Test
    void shouldPassValidFilterToProductService() {
        ProductSearchFilter generatedFilter = new ProductSearchFilter(
                "football",
                "Sports",
                new BigDecimal("100"),
                new BigDecimal("1000"),
                true,
                true
        );

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        when(productServiceClient.searchProductsByFilter(
                any(ProductSearchFilter.class),
                anyString()
        )).thenReturn(List.of());

        productSearchService.searchProductsByNaturalLanguage(
                "Find football under 1000",
                "Bearer test-token"
        );

        verify(productServiceClient).searchProductsByFilter(
                argThat(filter ->
                        filter.keyword().equals("football")
                                && filter.category().equals("Sports")
                                && filter.minPrice().compareTo(new BigDecimal("100")) == 0
                                && filter.maxPrice().compareTo(new BigDecimal("1000")) == 0
                                && Boolean.TRUE.equals(filter.inStock())
                                && Boolean.TRUE.equals(filter.active())
                ),
                eq("Bearer test-token")
        );
    }
}
