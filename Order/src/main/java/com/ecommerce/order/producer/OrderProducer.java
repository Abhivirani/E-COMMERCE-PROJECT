package com.ecommerce.order.producer;

import com.ecommerce.order.configuration.RabbitMQProperties;
import com.ecommerce.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderProducer {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;

    public void send(OrderPlacedEvent event) {

        System.out.println("Sending message to RabbitMQ: " + event);

        rabbitTemplate.convertAndSend(
                properties.getExchange().getName(),
                properties.getRouting().getOrder(),
                event
        );

        System.out.println("Message sent to RabbitMQ");
    }
}