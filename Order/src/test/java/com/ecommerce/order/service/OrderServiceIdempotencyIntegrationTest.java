
package com.ecommerce.order.service;

import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.Payment;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.producer.OrderProducer;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.rabbitmq.listener.direct.auto-startup=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'Asia/Kolkata'"
        }
)
@Testcontainers
class OrderServiceIdempotencyIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16")
                    .withDatabaseName("order_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private UserValidationService userValidationService;

    @MockitoBean
    private ProductValidationService productValidationService;

    @MockitoBean
    private OrderProducer orderProducer;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void concurrentRequestsWithSameIdempotencyKeyShouldReturnSameOrder()
            throws Exception {

        String userId = "concurrent-test-user";
        String idempotencyKey = "concurrent-checkout-key";

        // Ensure the test starts without an existing order.
        orderRepository.deleteAll();

        // Synchronize both requests after their initial idempotency lookup.
        CyclicBarrier validationBarrier = new CyclicBarrier(2);

        when(userValidationService.validateUser(userId))
                .thenAnswer(invocation -> {
                    validationBarrier.await(10, TimeUnit.SECONDS);
                    return true;
                });

        CartItem cartItem = new CartItem();
        cartItem.setUserId(userId);
        cartItem.setProductId("1");
        cartItem.setQuantity(1);
        cartItem.setPrice(new BigDecimal("799.00"));

        when(cartService.getCart(userId))
                .thenReturn(List.of(cartItem));

        when(productValidationService.validateProduct("1"))
                .thenReturn(true);

        Payment payment = mock(Payment.class);

        when(paymentService.processPayment(anyLong()))
                .thenReturn(payment);

        when(payment.getStatus())
                .thenReturn(PaymentStatus.SUCCESS);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Optional<com.ecommerce.order.dto.OrderResponse>> request =
                    () -> orderService.createOrder(userId, idempotencyKey);

            Future<Optional<com.ecommerce.order.dto.OrderResponse>> first =
                    executor.submit(request);

            Future<Optional<com.ecommerce.order.dto.OrderResponse>> second =
                    executor.submit(request);

            Optional<com.ecommerce.order.dto.OrderResponse> firstResponse =
                    first.get(30, TimeUnit.SECONDS);

            Optional<com.ecommerce.order.dto.OrderResponse> secondResponse =
                    second.get(30, TimeUnit.SECONDS);

            assertTrue(firstResponse.isPresent());
            assertTrue(secondResponse.isPresent());

            Long firstOrderId = firstResponse.get().getId();
            Long secondOrderId = secondResponse.get().getId();

            // Both requests must resolve to the same order.
            assertEquals(firstOrderId, secondOrderId);

            // PostgreSQL must contain only one order for this key.
            Optional<Order> storedOrder =
                    orderRepository.findByUserIdAndIdempotencyKey(
                            userId,
                            idempotencyKey
                    );

            assertTrue(storedOrder.isPresent());
            assertEquals(firstOrderId, storedOrder.get().getId());

            // Only one request should execute the Saga.
            verify(paymentService, times(1))
                    .processPayment(anyLong());

            verify(orderProducer, times(1))
                    .send(any(OrderPlacedEvent.class));

            verify(cartService, times(1))
                    .clearCart(userId);

        } finally {
            executor.shutdownNow();
        }
    }
}
