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

class ProductSearchServiceIntegrationTest {

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
                        chatClientBuilder
                );
    }

    @Test
    void shouldIntegrateAiSearchWithProductService() {

        ProductSearchFilter generatedFilter =
                new ProductSearchFilter(
                        "sports products football",
                        null,
                        null,
                        new BigDecimal("1000"),
                        true,
                        null
                );

        when(callResponseSpec.entity(ProductSearchFilter.class))
                .thenReturn(generatedFilter);

        ProductResponse football = mock(ProductResponse.class);

        when(productServiceClient.searchProductsByFilter(
                any(ProductSearchFilter.class),
                anyString()
        )).thenReturn(List.of(football));

        List<ProductResponse> result =
                productSearchService.searchProductsByNaturalLanguage(
                        "Find sports products under 1000 that are in stock",
                        "Bearer integration-test-token"
                );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(football, result.get(0));

        verify(chatClient).prompt();

        verify(productServiceClient).searchProductsByFilter(
                argThat(filter ->
                        "football".equals(filter.keyword())
                                && "Sports".equals(filter.category())
                                && filter.maxPrice()
                                .compareTo(new BigDecimal("1000")) == 0
                                && Boolean.TRUE.equals(filter.inStock())
                                && Boolean.TRUE.equals(filter.active())
                ),
                eq("Bearer integration-test-token")
        );
    }
}
