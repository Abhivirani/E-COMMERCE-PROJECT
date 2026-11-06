package com.ecommerce.order.configuration;

import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerRetryEvents() {

        retryRegistry.retry("productService")
                .getEventPublisher()
                .onRetry(event ->
                        System.out.println(
                                "Retry Attempt : "
                                        + event.getNumberOfRetryAttempts()
                        ));
    }
}