package com.ecommerce.notification.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange("ecommerce.exchange");
    }

    @Bean
    public Queue orderQueue() {
        return new Queue("order.queue");
    }

    @Bean
    public Binding orderBinding(
            Queue orderQueue,
            TopicExchange exchange
    ) {
        return BindingBuilder.bind(orderQueue)
                .to(exchange)
                .with("order.created");
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}