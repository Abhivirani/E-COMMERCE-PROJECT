package com.ecommerce.order.service;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaRecoveryService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${saga.recovery.fixed-delay:30000}")
    public void recoverFailedCompensations() {

        List<Order> orders = orderRepository.findAllByStatus(
                OrderStatus.COMPENSATION_REQUIRED
        );

        if (orders.isEmpty()) {
            return;
        }

        log.info(
                "Found {} orders requiring compensation recovery",
                orders.size()
        );

        for (Order order : orders) {
            try {
                List<String> reservations = new ArrayList<>();

                for (OrderItem item : order.getItems()) {
                    String reservationId =
                            "order-" + order.getId()
                                    + "-product-" + item.getProductId();

                    reservations.add(reservationId);
                }

                log.info(
                        "Retrying compensation for order {}",
                        order.getId()
                );

                boolean succeeded = orderService.compensateSaga(
                        order.getId(),
                        reservations
                );

                if (succeeded) {
                    order.setStatus(OrderStatus.FAILED);
                    orderRepository.save(order);

                    log.info(
                            "Compensation recovery succeeded for order {}",
                            order.getId()
                    );
                } else {
                    log.warn(
                            "Compensation remains incomplete for order {}. " +
                                    "It will be retried in the next recovery cycle.",
                            order.getId()
                    );
                }

            } catch (RuntimeException ex) {
                log.error(
                        "Unexpected error during compensation recovery for order {}",
                        order.getId(),
                        ex
                );
            }
        }
    }
}