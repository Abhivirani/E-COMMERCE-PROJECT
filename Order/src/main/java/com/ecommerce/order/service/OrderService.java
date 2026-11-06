package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderItemDTO;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.exception.InvalidUserException;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.model.*;
import com.ecommerce.order.producer.OrderProducer;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final UserValidationService userValidationService;
    private final ProductValidationService productValidationService;
    private final OrderProducer orderProducer;
    private final PaymentService paymentService;

    public Optional<OrderResponse> createOrder(String userId) {
        return createOrder(userId, UUID.randomUUID().toString());
    }

    public Optional<OrderResponse> createOrder(
            String userId,
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }

        Optional<Order> existingOrder =
                orderRepository.findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );

        if (existingOrder.isPresent()) {
            log.info(
                    "Idempotent checkout retry detected for user={}, orderId={}",
                    userId,
                    existingOrder.get().getId()
            );

            return Optional.of(mapToOrderResponse(existingOrder.get()));
        }

        if (!userValidationService.validateUser(userId)) {
            throw new InvalidUserException("Invalid User: " + userId);
        }

        List<CartItem> cartItems = cartService.getCart(userId);

        if (cartItems.isEmpty()) {
            return Optional.empty();
        }

        // Validate products and prices before starting the Saga.
        for (CartItem item : cartItems) {
            if (!productValidationService.validateProduct(item.getProductId())) {
                throw new ProductNotFoundException(
                        "Invalid product: " + item.getProductId());
            }

            productValidationService.validateStock(
                    Long.valueOf(item.getProductId()),
                    item.getQuantity());
        }

        BigDecimal totalPrice = cartItems.stream()
                .map(item -> item.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setIdempotencyKey(idempotencyKey);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(totalPrice);

        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> new OrderItem(
                        null,
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getPrice().multiply(
                                BigDecimal.valueOf(item.getQuantity())),
                        order))
                .toList();

        order.setItems(orderItems);

        Order savedOrder;

        try {
            savedOrder = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {

            Optional<Order> duplicate =
                    orderRepository.findByUserIdAndIdempotencyKey(
                            userId,
                            idempotencyKey
                    );

            if (duplicate.isPresent()) {
                log.info(
                        "Concurrent idempotent checkout detected. Returning existing orderId={}",
                        duplicate.get().getId()
                );

                return Optional.of(mapToOrderResponse(duplicate.get()));
            }

            throw ex;
        }

        List<String> reservations = new ArrayList<>();

        try {
            // Step 1: Reserve stock for each item.
            for (CartItem item : cartItems) {
                Long productId = Long.valueOf(item.getProductId());
                String reservationId =
                        "order-" + savedOrder.getId() + "-product-" + productId;

                // Track the ID before the remote call because the result
                // may be ambiguous if the response is lost.
                reservations.add(reservationId);

                productValidationService.reserveStock(
                        reservationId,
                        savedOrder.getId(),
                        productId,
                        item.getQuantity());
            }

            // Step 2: Create and process the simulated payment.
            paymentService.createPendingPayment(
                    savedOrder.getId(), totalPrice);

            Payment payment = paymentService.processPayment(savedOrder.getId());

            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                throw new IllegalStateException("Simulated payment failed");
            }

            // Step 3: Confirm all stock reservations.
            for (String reservationId : reservations) {
                productValidationService.confirmStock(reservationId);
            }

            // Step 4: Confirm the order after payment and stock confirmation.
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            savedOrder = orderRepository.save(savedOrder);

        } catch (RuntimeException originalFailure) {
            log.error(
                    "SAGA_FAILURE_HANDLER_ENTERED: orderId={}, currentStatus={}, reservations={}",
                    savedOrder.getId(),
                    savedOrder.getStatus(),
                    reservations,
                    originalFailure
            );

            boolean compensated;

            try {
                compensated = compensateSaga(savedOrder.getId(), reservations);

                log.info(
                        "SAGA_COMPENSATION_RESULT: orderId={}, compensated={}",
                        savedOrder.getId(),
                        compensated
                );
            } catch (RuntimeException compensationFailure) {
                log.error(
                        "SAGA_COMPENSATION_THREW_EXCEPTION: orderId={}",
                        savedOrder.getId(),
                        compensationFailure
                );

                compensated = false;
            }

            OrderStatus newStatus = compensated
                    ? OrderStatus.FAILED
                    : OrderStatus.COMPENSATION_REQUIRED;

            savedOrder.setStatus(newStatus);

            log.info(
                    "SAGA_STATUS_BEFORE_SAVE: orderId={}, status={}",
                    savedOrder.getId(),
                    savedOrder.getStatus()
            );

            try {
                Order savedOrderAfterFailure = orderRepository.saveAndFlush(savedOrder);

                log.info(
                        "SAGA_STATUS_AFTER_SAVE: orderId={}, status={}",
                        savedOrderAfterFailure.getId(),
                        savedOrderAfterFailure.getStatus()
                );
            } catch (RuntimeException persistenceFailure) {
                log.error(
                        "SAGA_STATUS_SAVE_FAILED: orderId={}, intendedStatus={}",
                        savedOrder.getId(),
                        newStatus,
                        persistenceFailure
                );

                throw persistenceFailure;
            }

            throw originalFailure;
        }

        // Post-confirmation operations: do not compensate a confirmed order.
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(savedOrder.getId());
        event.setUserId(savedOrder.getUserId());

        orderProducer.send(event);
        cartService.clearCart(userId);

        return Optional.of(mapToOrderResponse(savedOrder));

    }


    public boolean compensateSaga(
            Long orderId,
            List<String> reservations){

        boolean compensationSucceeded = true;

        try {
            paymentService.refundPayment(orderId);
        } catch (IllegalArgumentException ex) {
            // No payment record means no payment was created to refund.
            log.info("No payment record to compensate for order {}", orderId);
        } catch (RuntimeException ex) {
            compensationSucceeded = false;
            log.error("Refund failed for order {}", orderId, ex);
        }

        // Release every reservation, even if an earlier release fails.
        for (int i = reservations.size() - 1; i >= 0; i--) {
            String reservationId = reservations.get(i);

            try {
                String result =
                        productValidationService.compensateStock(reservationId);

                if ("RELEASED".equals(result)
                        || "ALREADY_RELEASED".equals(result)) {

                    log.info(
                            "Stock compensation completed for reservation {}: {}",
                            reservationId,
                            result
                    );

                } else {
                    compensationSucceeded = false;

                    log.warn(
                            "Stock compensation unresolved for reservation {}: {}",
                            reservationId,
                            result
                    );
                }

            } catch (RuntimeException ex) {
                compensationSucceeded = false;

                log.error(
                        "Stock compensation failed for reservation {}",
                        reservationId,
                        ex
                );
            }
        }

        return compensationSucceeded;
    }


    private OrderResponse mapToOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getItems().stream()
                        .map(orderItem -> new OrderItemDTO(
                                orderItem.getId(),
                                orderItem.getProductId(),
                                orderItem.getQuantity(),
                                orderItem.getPrice(),
                                orderItem.getSubTotal()
                        )).toList(),
                order.getCreatedAt()
        );
    }
}
