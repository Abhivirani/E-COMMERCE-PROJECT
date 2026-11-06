package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.StockReservation;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.StockReservationRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigDecimal;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5434/product_testdb",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ProductServiceConcurrencyIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    private Long productId;
    @BeforeAll
    static void setUpTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setName("Concurrency Test Product");
        product.setDescription("Used for PostgreSQL concurrency testing");
        product.setCategory("TEST");
        product.setPrice(new BigDecimal("100.00"));
        product.setStockQuantity(5);
        product.setImageUrl("test-image.png");
        product.setActive(true);

        productId = productRepository.saveAndFlush(product).getId();
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void concurrentReservationsMustNotOversellStock() throws Exception {

        CountDownLatch startSignal = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> firstRequest = executor.submit(() -> {
                startSignal.await();

                try {
                    productService.reserveStock(
                            "concurrency-reservation-A",
                            1001L,
                            productId,
                            4
                    );

                    return null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            });

            Future<Throwable> secondRequest = executor.submit(() -> {
                startSignal.await();

                try {
                    productService.reserveStock(
                            "concurrency-reservation-B",
                            1002L,
                            productId,
                            4
                    );

                    return null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            });

            // Release both workers so their reservation requests overlap.
            startSignal.countDown();

            Throwable firstFailure = firstRequest.get();
            Throwable secondFailure = secondRequest.get();

            int successfulRequests = 0;
            int failedRequests = 0;

            if (firstFailure == null) {
                successfulRequests++;
            } else {
                failedRequests++;
            }

            if (secondFailure == null) {
                successfulRequests++;
            } else {
                failedRequests++;
            }

            assertEquals(
                    1,
                    successfulRequests,
                    "Exactly one reservation should succeed"
            );

            assertEquals(
                    1,
                    failedRequests,
                    "Exactly one reservation should fail"
            );

            assertTrue(
                    firstFailure == null
                            || firstFailure.getMessage().contains("Insufficient stock"),
                    "The first request should either succeed or fail due to insufficient stock"
            );

            assertTrue(
                    secondFailure == null
                            || secondFailure.getMessage().contains("Insufficient stock"),
                    "The second request should either succeed or fail due to insufficient stock"
            );

            Product savedProduct = productRepository.findById(productId)
                    .orElseThrow();

            assertEquals(
                    1,
                    savedProduct.getStockQuantity(),
                    "Stock must remain at 1; it must never be oversold"
            );

            List<StockReservation> reservations =
                    reservationRepository.findAll();

            assertEquals(
                    1,
                    reservations.size(),
                    "Exactly one reservation should be recorded"
            );
        }finally {
        executor.shutdownNow();
    }
    }

    @Test
    void productVersionMustInitializeAndIncrementOnUpdate() {
        Product product = productRepository.findById(productId)
                .orElseThrow();

        assertNotNull(product.getVersion(),
                "Product version must be initialized after creation");

        Long initialVersion = product.getVersion();

        product.setName("Updated Concurrency Test Product");
        productRepository.saveAndFlush(product);

        Product updatedProduct = productRepository.findById(productId)
                .orElseThrow();

        assertNotNull(updatedProduct.getVersion(),
                "Product version must not be null after update");

        assertTrue(updatedProduct.getVersion() > initialVersion,
                "Product version must increase after update");
    }

    @Test
    void productVersionMustIncrementOnStockReservation() {
        Product productBefore = productRepository.findById(productId)
                .orElseThrow();

        Long initialVersion = productBefore.getVersion();

        productService.reserveStock(
                "version-test-reservation",
                2001L,
                productId,
                2
        );

        Product productAfter = productRepository.findById(productId)
                .orElseThrow();

        assertNotNull(productAfter.getVersion(),
                "Product version must not be null after reservation");

        assertTrue(productAfter.getVersion() > initialVersion,
                "Product version must increase after stock reservation");

        assertEquals(3, productAfter.getStockQuantity(),
                "Stock must decrease from 5 to 3");
    }

    @Test
    void productVersionMustRemainValidAcrossRepeatedUpdates() {

        Product product = productRepository.findById(productId)
                .orElseThrow();

        Long previousVersion = product.getVersion();

        assertNotNull(
                previousVersion,
                "Initial product version must not be null"
        );

        for (int i = 1; i <= 5; i++) {

            Product currentProduct = productRepository.findById(productId)
                    .orElseThrow();

            currentProduct.setName("Updated Product " + i);

            productRepository.saveAndFlush(currentProduct);

            Product updatedProduct = productRepository.findById(productId)
                    .orElseThrow();

            assertNotNull(
                    updatedProduct.getVersion(),
                    "Product version must not be null after update " + i
            );

            assertTrue(
                    updatedProduct.getVersion() > previousVersion,
                    "Product version must increase after update " + i
            );

            previousVersion = updatedProduct.getVersion();
        }
    }
}