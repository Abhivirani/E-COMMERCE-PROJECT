package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ecommerce.order.service.ProductValidationService;


import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final ProductValidationService productValidationService;


    @PutMapping("/diagnostics/stock-compensation")
    public ResponseEntity<String> testStockCompensation() {
        productValidationService.compensateStock(
                "diagnostic-nonexistent-reservation"
        );

        return ResponseEntity.ok(
                "Stock compensation request completed successfully"
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<OrderResponse> createOrder(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String userId = authentication.getName();

        return orderService.createOrder(userId, idempotencyKey)
                .map(orderResponse ->
                        new ResponseEntity<>(orderResponse, HttpStatus.CREATED))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }


    @GetMapping("/me")
    public ResponseEntity<?> me(
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        System.out.println("Authorization Header = " + authorization);
        System.out.println("Authentication = " + authentication);

        if (authentication == null) {
            return ResponseEntity.ok("Authentication is NULL");
        }

        return ResponseEntity.ok(Map.of(
                "name", authentication.getName(),
                "authenticated", authentication.isAuthenticated(),
                "authorities", authentication.getAuthorities()
        ));
    }

    @GetMapping("/headers")
    public Map<String, String> headers(HttpServletRequest request) {

        Map<String, String> headers = new HashMap<>();

        Enumeration<String> names = request.getHeaderNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }

        return headers;
    }
}
