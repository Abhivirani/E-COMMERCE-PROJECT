package com.ecommerce.order.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final RabbitMQProperties properties;

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(
                properties.getExchange().getName()
        );
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(
                properties.getQueue().getOrder()
        );
    }

    @Bean
    public Binding orderBinding(
            Queue orderQueue,
            TopicExchange exchange
    ) {
        return BindingBuilder
                .bind(orderQueue)
                .to(exchange)
                .with(properties.getRouting().getOrder());
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}