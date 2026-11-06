package com.ecommerce.order.service;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaRecoveryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private SagaRecoveryService sagaRecoveryService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(12L);
        order.setUserId("test-user");
        order.setTotalAmount(new BigDecimal("799.00"));
        order.setStatus(OrderStatus.COMPENSATION_REQUIRED);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setProductId("1");
        item.setQuantity(1);
        item.setPrice(new BigDecimal("799.00"));
        item.setSubTotal(new BigDecimal("799.00"));
        item.setOrder(order);

        order.setItems(List.of(item));
    }

    @Test
    void shouldDoNothingWhenNoOrdersRequireRecovery() {
        when(orderRepository.findAllByStatus(
                OrderStatus.COMPENSATION_REQUIRED
        )).thenReturn(List.of());

        sagaRecoveryService.recoverFailedCompensations();

        verify(orderService, never()).compensateSaga(anyLong(), anyList());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldMarkOrderFailedWhenCompensationSucceeds() {
        when(orderRepository.findAllByStatus(
                OrderStatus.COMPENSATION_REQUIRED
        )).thenReturn(List.of(order));

        when(orderService.compensateSaga(
                12L,
                List.of("order-12-product-1")
        )).thenReturn(true);

        sagaRecoveryService.recoverFailedCompensations();

        assertEquals(OrderStatus.FAILED, order.getStatus());

        verify(orderService).compensateSaga(
                12L,
                List.of("order-12-product-1")
        );

        verify(orderRepository).save(order);
    }

    @Test
    void shouldKeepOrderCompensationRequiredWhenCompensationFails() {
        when(orderRepository.findAllByStatus(
                OrderStatus.COMPENSATION_REQUIRED
        )).thenReturn(List.of(order));

        when(orderService.compensateSaga(
                12L,
                List.of("order-12-product-1")
        )).thenReturn(false);

        sagaRecoveryService.recoverFailedCompensations();

        assertEquals(
                OrderStatus.COMPENSATION_REQUIRED,
                order.getStatus()
        );

        verify(orderService).compensateSaga(
                12L,
                List.of("order-12-product-1")
        );

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldKeepOrderEligibleForRecoveryWhenCompensationThrows() {
        when(orderRepository.findAllByStatus(
                OrderStatus.COMPENSATION_REQUIRED
        )).thenReturn(List.of(order));

        when(orderService.compensateSaga(
                12L,
                List.of("order-12-product-1")
        )).thenThrow(new RuntimeException("Temporary service failure"));

        sagaRecoveryService.recoverFailedCompensations();

        assertEquals(
                OrderStatus.COMPENSATION_REQUIRED,
                order.getStatus()
        );

        verify(orderRepository, never()).save(any(Order.class));
    }
}