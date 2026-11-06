package com.ecommerce.notification.consumer;

import com.ecommerce.notification.event.OrderPlacedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {

    @RabbitListener(queues = "order.queue")
    public void consume(OrderPlacedEvent event) {

        System.out.println("=================================");
        System.out.println("Message Received From RabbitMQ");
        System.out.println(event);
        System.out.println("Email sent for order: " + event.getOrderId());
        System.out.println("=================================");
    }
}