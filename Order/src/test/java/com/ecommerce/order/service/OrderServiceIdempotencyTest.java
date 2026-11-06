
package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.producer.OrderProducer;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.model.Payment;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.event.OrderPlacedEvent;
import static org.mockito.Mockito.times;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceIdempotencyTest {

    @Mock
    private CartService cartService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserValidationService userValidationService;

    @Mock
    private ProductValidationService productValidationService;

    @Mock
    private OrderProducer orderProducer;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private OrderService orderService;

    private static final String USER_ID = "test-user";
    private static final String IDEMPOTENCY_KEY = "checkout-key-123";

    @Test
    void shouldReturnExistingOrderWhenSameIdempotencyKeyIsUsed() {

        // Arrange
        Order existingOrder = new Order();
        existingOrder.setId(100L);
        existingOrder.setUserId(USER_ID);
        existingOrder.setIdempotencyKey(IDEMPOTENCY_KEY);
        existingOrder.setTotalAmount(new BigDecimal("799.00"));
        existingOrder.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(existingOrder));

        // Act
        Optional<OrderResponse> response =
                orderService.createOrder(USER_ID, IDEMPOTENCY_KEY);

        // Assert
        assertTrue(response.isPresent());

        verify(orderRepository).findByUserIdAndIdempotencyKey(
                USER_ID,
                IDEMPOTENCY_KEY
        );

        // Existing order should be returned without starting another Saga.
        verify(userValidationService, never()).validateUser(anyString());
        verify(cartService, never()).getCart(anyString());
        verify(productValidationService, never()).reserveStock(
                anyString(), anyLong(), anyLong(), anyInt()
        );
        verify(paymentService, never()).processPayment(anyLong());
        verify(orderProducer, never()).send(any());
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(USER_ID, " ")
        );

        verify(orderRepository, never())
                .findByUserIdAndIdempotencyKey(anyString(), anyString());

        verify(userValidationService, never()).validateUser(anyString());
    }


    @Test
    void shouldCreateNewOrderWhenDifferentIdempotencyKeyIsUsed() {

        String differentKey = "checkout-key-456";

        // Arrange: No existing order for this key.
        when(orderRepository.findByUserIdAndIdempotencyKey(
                USER_ID,
                differentKey
        )).thenReturn(Optional.empty());

        when(userValidationService.validateUser(USER_ID))
                .thenReturn(true);

        CartItem cartItem = new CartItem();
        cartItem.setUserId(USER_ID);
        cartItem.setProductId("1");
        cartItem.setQuantity(1);
        cartItem.setPrice(new BigDecimal("799.00"));

        when(cartService.getCart(USER_ID))
                .thenReturn(List.of(cartItem));

        when(productValidationService.validateProduct("1"))
                .thenReturn(true);

        // Simulate generated ID for the new order.
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    if (order.getId() == null) {
                        order.setId(200L);
                    }
                    return order;
                });

        Payment payment = mock(Payment.class);

        when(paymentService.processPayment(200L))
                .thenReturn(payment);

        when(payment.getStatus())
                .thenReturn(PaymentStatus.SUCCESS);

        // Act
        Optional<OrderResponse> response =
                orderService.createOrder(USER_ID, differentKey);

        // Assert
        assertTrue(response.isPresent());

        verify(orderRepository, times(2)).save(any(Order.class));

        verify(productValidationService).reserveStock(
                "order-200-product-1",
                200L,
                1L,
                1
        );

        verify(paymentService).processPayment(200L);

        verify(productValidationService).confirmStock(
                "order-200-product-1"
        );

        verify(orderProducer).send(any(OrderPlacedEvent.class));

        verify(cartService).clearCart(USER_ID);
    }

}
