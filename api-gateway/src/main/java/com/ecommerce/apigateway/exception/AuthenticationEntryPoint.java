package com.ecommerce.apigateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class AuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange,
                               AuthenticationException ex) {

        ErrorResponse response = new ErrorResponse(
                "Authentication Required",
                HttpStatus.UNAUTHORIZED.value(),
                LocalDateTime.now()
        );

        var serverResponse = exchange.getResponse();

        serverResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
        serverResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {

            byte[] bytes = objectMapper.writeValueAsBytes(response);

            return serverResponse.writeWith(
                    Mono.just(serverResponse.bufferFactory().wrap(bytes))
            );

        } catch (Exception e) {

            return Mono.error(e);
        }
    }
}