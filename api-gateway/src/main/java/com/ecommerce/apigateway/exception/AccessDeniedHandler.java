package com.ecommerce.apigateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class AccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;
    public AccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public Mono<Void> handle(ServerWebExchange exchange,
                             AccessDeniedException ex) {

        ErrorResponse response = new ErrorResponse(
                "Access Denied",
                HttpStatus.FORBIDDEN.value(),
                LocalDateTime.now()
        );

        var serverResponse = exchange.getResponse();

        serverResponse.setStatusCode(HttpStatus.FORBIDDEN);
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