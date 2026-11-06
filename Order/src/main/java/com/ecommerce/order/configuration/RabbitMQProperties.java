package com.ecommerce.order.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitMQProperties {

    private Exchange exchange = new Exchange();
    private Queue queue = new Queue();
    private Routing routing = new Routing();

    @Getter
    @Setter
    public static class Exchange {
        private String name;
    }

    @Getter
    @Setter
    public static class Queue {
        private String order;
        private String notification;
    }

    @Getter
    @Setter
    public static class Routing {
        private String order;
        private String notification;
    }
}
