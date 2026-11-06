
package com.ecommerce.order.configuration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .filter(bearerTokenFilter());
    }

    @Bean
    public ExchangeFilterFunction bearerTokenFilter() {
        return (request, next) -> {

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes)
                            RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return next.exchange(request);
            }

            String token = attributes.getRequest()
                    .getHeader(HttpHeaders.AUTHORIZATION);

            if (token == null || token.isBlank()) {
                return next.exchange(request);
            }

            ClientRequest newRequest = ClientRequest.from(request)
                    .headers(headers ->
                            headers.set(HttpHeaders.AUTHORIZATION, token))
                    .build();

            return next.exchange(newRequest);
        };
    }

    @Bean
    public WebClient userServiceWebClient(
            WebClient.Builder webClientBuilder) {

        return webClientBuilder
                .baseUrl("http://USER")
                .build();
    }

    @Bean
    public WebClient productServiceWebClient(
            WebClient.Builder webClientBuilder) {

        return webClientBuilder
                .baseUrl("http://PRODUCT")
                .build();
    }


    @Bean
    public WebClient stockReservationWebClient(
            @LoadBalanced WebClient.Builder webClientBuilder,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        return webClientBuilder.clone()
                .filter((request, next) -> {

                    OAuth2AuthorizeRequest authorizeRequest =
                            OAuth2AuthorizeRequest
                                    .withClientRegistrationId("keycloak")
                                    .principal("order-service")
                                    .build();

                    var authorizedClient =
                            authorizedClientManager.authorize(authorizeRequest);

                    if (authorizedClient == null) {
                        return reactor.core.publisher.Mono.error(
                                new IllegalStateException(
                                        "Unable to obtain Order Service access token"));
                    }

                    String token = authorizedClient.getAccessToken()
                            .getTokenValue();
                    com.fasterxml.jackson.databind.JsonNode claims = null;
                    try {
                        String[] parts = token.split("\\.");
                        String payload = new String(
                                java.util.Base64.getUrlDecoder().decode(parts[1]),
                                java.nio.charset.StandardCharsets.UTF_8
                        );

                        claims = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(payload);

                        System.out.println("=== Service Account Claims ===");
                        System.out.println("Issuer: " + claims.path("iss").asText());
                        System.out.println("Audience: " + claims.path("aud").toString());
                        System.out.println("Authorized party: " + claims.path("azp").asText());
                        System.out.println("Realm roles: "
                                + claims.path("realm_access").path("roles").toString());
                        System.out.println("===============================");
                    } catch (Exception ex) {
                        System.out.println("Unable to inspect service-account token claims.");
                    }
                    var accessToken = authorizedClient.getAccessToken();

                    System.out.println("=== Stock Reservation Token Diagnostics ===");
                    System.out.println("Token type: " + accessToken.getTokenType());
                    System.out.println("Token expiration: " + accessToken.getExpiresAt());
                    System.out.println("============================================");

                    ClientRequest authenticatedRequest =
                            ClientRequest.from(request)
                                    .headers(headers -> headers.setBearerAuth(token))
                                    .build();

                    System.out.println("Stock reservation request URI: "
                            + authenticatedRequest.url());
                    System.out.println("Service-account token issuer: "
                            + claims.path("iss").asText());

                    return next.exchange(authenticatedRequest);
                })
                .baseUrl("http://PRODUCT")
                .build();
    }
}
