
package com.ecommerce.aiassistant.service;

import com.ecommerce.aiassistant.dto.ProductSearchFilter;
import com.ecommerce.aiassistant.dto.ProductResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

import java.util.List;

@Service
public class ProductServiceClient {

    private final WebClient webClient;
    private static final Logger log =
            LoggerFactory.getLogger(ProductServiceClient.class);

    public ProductServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${product.service.base-url:http://localhost:8081}")
            String productServiceBaseUrl) {

        this.webClient = webClientBuilder
                .baseUrl(productServiceBaseUrl)
                .build();
    }

    public List<ProductResponse> getAllProducts() {
        return webClient.get()
                .uri("/api/products")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponse>>() {})
                .block();
    }

    public List<ProductResponse> searchProducts(String keyword) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/products/search")
                        .queryParam("keyword", keyword)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductResponse>>() {})
                .block();
    }



    public List<ProductResponse> searchProductsByFilter(
            ProductSearchFilter filter,
            String authorization) {

        try {
            List<ProductResponse> products = webClient.post()
                    .uri("/api/products/search/filter")
                    .headers(headers ->
                            headers.set("Authorization", authorization))
                    .bodyValue(filter)
                    .retrieve()
                    .bodyToMono(
                            new ParameterizedTypeReference<List<ProductResponse>>() {})
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return products == null ? List.of() : products;

        } catch (WebClientResponseException exception) {

            log.error("Product Service returned HTTP status {}",
                    exception.getStatusCode().value());

            throw new IllegalStateException(
                    "Product Service rejected or failed the search request");

        } catch (Exception exception) {

            log.error("Product Service call failed: {}",
                    exception.getClass().getSimpleName());

            throw new IllegalStateException(
                    "Product Service is temporarily unavailable");
        }
    }


}
