package com.ecommerce.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.ecommerce.order.exception.InvalidUserException;

@Service
@Slf4j
public class UserValidationService {
    private final WebClient userServiceWebClient;

    public UserValidationService(
            @Qualifier("userServiceWebClient")
            WebClient userServiceWebClient) {

        this.userServiceWebClient = userServiceWebClient;
    }

    public boolean validateUser(String userId)
    {
        log.info("Validating user {}", userId);
        try {
            Boolean response = userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            log.info("Validation response for user {} : {}", userId, response);
            return Boolean.TRUE.equals(response);

        } catch (WebClientResponseException e) {
            log.error("Error validating user {}. Status: {}",
                    userId,
                    e.getStatusCode());

            if(e.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new InvalidUserException("User Not Found: " + userId);
            }
            if(e.getStatusCode() == HttpStatusCode.valueOf(400)) {
                throw new InvalidUserException("Invalid Request: " + userId);
            }
            throw new RuntimeException("User Service Error");
        }
    }
}
