package com.ecommerce.order.service;

import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.Payment;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.producer.OrderProducer;
import com.ecommerce.order.repository.OrderRepository;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceSagaTest {

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

    @Mock
    private Payment payment;

    @InjectMocks
    private OrderService orderService;

    private static final String USER_ID = "test-user";
    private static final String PRODUCT_ID = "1";
    private static final Long ORDER_ID = 123L;
    private static final String RESERVATION_ID =
            "order-123-product-1";
    private final List<OrderStatus> statusesAtSave = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CartItem cartItem = new CartItem();
        cartItem.setUserId(USER_ID);
        cartItem.setProductId(PRODUCT_ID);
        cartItem.setQuantity(1);
        cartItem.setPrice(new BigDecimal("799.00"));
        statusesAtSave.clear();

        when(userValidationService.validateUser(USER_ID))
                .thenReturn(true);

        when(cartService.getCart(USER_ID))
                .thenReturn(List.of(cartItem));

        when(productValidationService.validateProduct(PRODUCT_ID))
                .thenReturn(true);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    statusesAtSave.add(order.getStatus());
                    // Capture the status at the moment save() is called.
                    OrderStatus statusAtSave = order.getStatus();

                    // Simulate the database-generated order ID.
                    if (order.getId() == null) {
                        order.setId(ORDER_ID);
                    }

                    // Preserve the initial status independently of later mutations.
                    if (statusAtSave == OrderStatus.PENDING) {
                        order.setId(ORDER_ID);
                    }

                    return order;
                });

        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldMarkOrderFailedWhenSagaFailsButCompensationSucceeds() {


        when(paymentService.processPayment(ORDER_ID)).thenReturn(payment);
        when(payment.getStatus()).thenReturn(PaymentStatus.SUCCESS);

        // Arrange: stock confirmation fails after payment succeeds.
        doThrow(new RuntimeException("Stock confirmation failed"))
                .when(productValidationService)
                .confirmStock(RESERVATION_ID);
        when(productValidationService.compensateStock(RESERVATION_ID))
                .thenReturn("RELEASED");
        // Act: the original failure should be propagated.
        assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(USER_ID)
        );

        // Assert: refund and stock compensation were attempted.
        verify(paymentService).refundPayment(ORDER_ID);

        verify(productValidationService)
                .compensateStock(RESERVATION_ID);

        // The order should have been saved initially and after failure.
        // Verify the initial PENDING order was saved.
        ArgumentCaptor<Order> initialOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(initialOrderCaptor.capture());
        assertEquals(OrderStatus.PENDING, statusesAtSave.get(0));

        // Verify the final FAILED order was saved and flushed.
        ArgumentCaptor<Order> finalOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).saveAndFlush(finalOrderCaptor.capture());

        Order finalOrder = finalOrderCaptor.getValue();

        assertEquals(OrderStatus.FAILED, finalOrder.getStatus());

        // A failed order must not publish an order event or clear the cart.
        verify(orderProducer, never()).send(any());

        verify(cartService, never()).clearCart(USER_ID);
    }

    @Test
    void shouldMarkOrderCompensationRequiredWhenStockCompensationFails() {

        when(paymentService.processPayment(ORDER_ID)).thenReturn(payment);
        when(payment.getStatus()).thenReturn(PaymentStatus.SUCCESS);

        // Arrange: confirmation fails.
        doThrow(new RuntimeException("Stock confirmation failed"))
                .when(productValidationService)
                .confirmStock(RESERVATION_ID);

        // Stock compensation also fails.
        doThrow(new RuntimeException("Stock compensation failed"))
                .when(productValidationService)
                .compensateStock(RESERVATION_ID);

        // Act: the original confirmation failure should be propagated.
        assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(USER_ID)
        );

        // Assert: refund was attempted despite the stock failure.
        verify(paymentService).refundPayment(ORDER_ID);

        verify(productValidationService)
                .compensateStock(RESERVATION_ID);

        // Verify the initial PENDING order was saved.
        ArgumentCaptor<Order> initialOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(initialOrderCaptor.capture());
        assertEquals(OrderStatus.PENDING, statusesAtSave.get(0));

        // Verify the final COMPENSATION_REQUIRED order was saved and flushed.
        ArgumentCaptor<Order> finalOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).saveAndFlush(finalOrderCaptor.capture());

        Order finalOrder = finalOrderCaptor.getValue();

        assertEquals(
                OrderStatus.COMPENSATION_REQUIRED,
                finalOrder.getStatus()
        );

        verify(orderProducer, never()).send(any());

        verify(cartService, never()).clearCart(USER_ID);
    }


    @Test
    void shouldCompensateStockWhenPaymentFails() {

        // Arrange: payment processing returns a failed payment.
        when(paymentService.processPayment(ORDER_ID)).thenReturn(payment);
        when(payment.getStatus())
                .thenReturn(PaymentStatus.FAILED);
        when(productValidationService.compensateStock(RESERVATION_ID))
                .thenReturn("RELEASED");
        // Act: payment failure should trigger Saga compensation.
        assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(USER_ID)
        );

        // Assert: stock compensation should be attempted.
        verify(productValidationService)
                .compensateStock(RESERVATION_ID);

        // Verify that payment refund was attempted.
        verify(paymentService).refundPayment(ORDER_ID);

        // Verify the final order status.
        // Verify the initial PENDING order was saved.
        ArgumentCaptor<Order> initialOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(initialOrderCaptor.capture());
        assertEquals(OrderStatus.PENDING, statusesAtSave.get(0));


        // Verify the final FAILED order was saved and flushed.
        ArgumentCaptor<Order> finalOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).saveAndFlush(finalOrderCaptor.capture());

        Order finalOrder = finalOrderCaptor.getValue();

        assertEquals(OrderStatus.FAILED, finalOrder.getStatus());

        // A failed order must not publish an order event or clear the cart.
        verify(orderProducer, never()).send(any());

        verify(cartService, never()).clearCart(USER_ID);

        // Stock confirmation must not be attempted after payment failure.
        verify(productValidationService, never())
                .confirmStock(RESERVATION_ID);

    }

    @Test
    void shouldMarkOrderFailedWhenInitialStockReservationFails() {
        // Arrange: the Product Service fails during the initial reservation.
        doThrow(new RuntimeException("Stock reservation failed"))
                .when(productValidationService)
                .reserveStock(
                        RESERVATION_ID,
                        ORDER_ID,
                        1L,
                        1
                );
        when(productValidationService.compensateStock(RESERVATION_ID))
                .thenReturn("RELEASED");
        // Act: the original reservation failure should be propagated.
        assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(USER_ID)
        );

        // Assert: compensation should be attempted for the reservation.
        verify(productValidationService)
                .compensateStock(RESERVATION_ID);

        // No payment should be created because reservation failed first.
        verify(paymentService, never())
                .createPendingPayment(any(), any());

        // The order should be saved initially and again after failure.
        // Verify the initial PENDING order was saved.
        ArgumentCaptor<Order> initialOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(initialOrderCaptor.capture());
        assertEquals(OrderStatus.PENDING, statusesAtSave.get(0));

        // Verify the final FAILED order was saved and flushed.
        ArgumentCaptor<Order> finalOrderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).saveAndFlush(finalOrderCaptor.capture());

        Order finalOrder = finalOrderCaptor.getValue();

        assertEquals(OrderStatus.FAILED, finalOrder.getStatus());

        // Failed orders must not publish events or clear the cart.
        verify(orderProducer, never()).send(any());
        verify(cartService, never()).clearCart(USER_ID);
    }

}