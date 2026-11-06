package com.ecommerce.order.service;

import com.ecommerce.order.dto.ProductResponse;
import com.ecommerce.order.dto.StockReservationRequest;
import com.ecommerce.order.exception.ProductServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.ecommerce.order.exception.InsufficientStockException;
import com.ecommerce.order.exception.ProductNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import reactor.core.publisher.Mono;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ProductValidationService {

    private final WebClient productServiceWebclient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final WebClient stockReservationWebClient;

    public ProductValidationService(
            @Qualifier("productServiceWebClient")
            WebClient productServiceWebClient,
            @Qualifier("stockReservationWebClient")
            WebClient stockReservationWebClient,
            CircuitBreakerRegistry circuitBreakerRegistry) {

        this.productServiceWebclient = productServiceWebClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.stockReservationWebClient = stockReservationWebClient;
    }

    @Retry(
            name = "productService",
            fallbackMethod = "validateProductFallback"
    )
    @CircuitBreaker(name = "productService")
    public boolean validateProduct(String productId) {

        log.info("Validating product {}", productId);

        Boolean response = productServiceWebclient.get()
                .uri("/api/products/{productId}/validate", productId)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        clientResponse -> Mono.error(
                                new ProductNotFoundException(
                                        "Product not found: " + productId))
                )
                .bodyToMono(Boolean.class)
                .block();

        return Boolean.TRUE.equals(response);
    }


    private boolean validateProductFallback(
            String productId,
            Throwable ex) {

        log.error(
                "Fallback executed for product {}. Cause={}, Circuit={}",
                productId,
                ex.getMessage(),
                circuitBreakerRegistry
                        .circuitBreaker("productService")
                        .getState()
        );

        // Preserve 404 when the product genuinely does not exist.
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ProductNotFoundException) {
                throw (ProductNotFoundException) cause;
            }
            cause = cause.getCause();
        }

        // Other failures indicate that product validation could not be completed.
        throw new ProductServiceUnavailableException(
                "Product Service is temporarily unavailable. Please try again later.",
                ex
        );
    }


    @TimeLimiter(
            name = "productService",
            fallbackMethod = "getProductTimeoutFallback"
    )
    @Retry(name = "productService")
    @CircuitBreaker(
            name = "productService",
            fallbackMethod = "getProductFallback"
    )
    public CompletableFuture<ProductResponse> getProduct(String productId){

        log.info("Fetching product {}", productId);

        return productServiceWebclient.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .bodyToMono(ProductResponse.class)
                .toFuture();
    }

    private CompletableFuture<ProductResponse> getProductFallback(
            String productId,
            Throwable ex) {

        log.error(
                "Product Service unavailable for product {}. Cause={}",
                productId,
                ex.getMessage()
        );

        log.error(
                "Circuit state: {}",
                circuitBreakerRegistry
                        .circuitBreaker("productService")
                        .getState()
        );

        return CompletableFuture.failedFuture(
                new RuntimeException(
                        "Product Service is temporarily unavailable.",
                        ex
                )
        );
    }

    private CompletableFuture<ProductResponse> getProductTimeoutFallback(
            String productId,
            Throwable ex) {

        log.error(
                "Timeout while fetching product {}. Cause={}",
                productId,
                ex.getMessage()
        );

        return CompletableFuture.failedFuture(
                new RuntimeException(
                        "Product Service timeout."
                )
        );
    }

    @CircuitBreaker(
            name = "productService",
            fallbackMethod = "reduceStockFallback"
    )
    public void reduceStock(Long productId, Integer quantity) {

        log.info("Reducing stock for product {}", productId);

        productServiceWebclient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/products/{id}/reduce-stock")
                        .queryParam("quantity", quantity)
                        .build(productId))
                .retrieve()
                .bodyToMono(Void.class)
                .block();

        log.info("Stock reduced successfully");
    }

    private void reduceStockFallback(
            Long productId,
            Integer quantity,
            Throwable ex) {

        log.error(
                "Cannot reduce stock. Product Service unavailable."
        );

        log.error(
                "Fallback executed for product {}, quantity {}. Cause={}, Circuit={}",
                productId,
                quantity,
                ex.getMessage(),
                circuitBreakerRegistry
                        .circuitBreaker("productService")
                        .getState()
        );

        throw new RuntimeException(
                "Stock service unavailable."
        );
    }

    public void validateStock(Long productId, Integer quantity) {

        ProductResponse product =
                getProduct(String.valueOf(productId)).join();

        if(product == null) {
            throw new ProductNotFoundException(
                    "Product not found: " + productId
            );
        }

        if(product.getStockQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product: " + productId
            );
        }
    }


    public void reserveStock(
            String reservationId,
            Long orderId,
            Long productId,
            Integer quantity) {

        StockReservationRequest request = new StockReservationRequest(
                reservationId, orderId, productId, quantity);

        log.info(
                "STOCK_RESERVATION_REQUEST: reservationId={}, orderId={}, productId={}, quantity={}",
                reservationId, orderId, productId, quantity);

        stockReservationWebClient.post()
                .uri("/api/products/reservations")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response ->
                        log.info(
                                "STOCK_RESERVATION_RESPONSE: reservationId={}, status={}",
                                reservationId, response.getStatusCode()))
                .doOnError(error ->
                        log.error(
                                "STOCK_RESERVATION_FAILED: reservationId={}, error={}",
                                reservationId, error.getMessage(), error))
                .block();
    }

    public void confirmStock(String reservationId) {

        log.info(
                "STOCK_CONFIRM_REQUEST: reservationId={}",
                reservationId);

        stockReservationWebClient.put()
                .uri("/api/products/reservations/{reservationId}/confirm",
                        reservationId)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response ->
                        log.info(
                                "STOCK_CONFIRM_RESPONSE: reservationId={}, status={}",
                                reservationId, response.getStatusCode()))
                .doOnError(error ->
                        log.error(
                                "STOCK_CONFIRM_FAILED: reservationId={}, error={}",
                                reservationId, error.getMessage(), error))
                .block();
    }

    public void releaseStock(String reservationId) {
        stockReservationWebClient.put()
                .uri("/api/products/reservations/{reservationId}/release",
                        reservationId)
                .retrieve()
                .toBodilessEntity()
                .block();
    }


    public String compensateStock(String reservationId) {

        String result = stockReservationWebClient
                .put()
                .uri(
                        "/api/products/reservations/{reservationId}/compensate",
                        reservationId
                )
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (result == null || result.isBlank()) {
            throw new IllegalStateException(
                    "Product Service returned an empty compensation result for reservation "
                            + reservationId
            );
        }

        // Normalize the response in case it is a JSON string.
        result = result.trim();

        if (result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }

        log.info(
                "Compensation result for reservation {}: {}",
                reservationId,
                result
        );

        return result;
    }


}
