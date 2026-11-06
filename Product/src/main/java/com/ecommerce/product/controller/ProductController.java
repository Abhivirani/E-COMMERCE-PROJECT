package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.model.CompensationResult;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import com.ecommerce.product.dto.StockReservationRequest;
import com.ecommerce.product.model.StockReservation;
import com.ecommerce.product.dto.ProductSearchFilter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAllProduct() {
        return new ResponseEntity<>(
                productService.getAllProduct(),
                HttpStatus.OK
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest productRequest)
    {
        return new ResponseEntity<ProductResponse>(productService.createProduct(productRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id, @RequestBody ProductRequest productRequest)
    {
        return productService.updateProduct(id,productRequest)
                .map(ResponseEntity::ok)
                .orElseGet(()-> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id)
    {
        productService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String keyword)
    {
        return ResponseEntity.ok(productService.searchProducts(keyword));
    }


    @PostMapping("/search/filter")
    public ResponseEntity<List<ProductResponse>> searchProductsByFilter(
            @RequestBody ProductSearchFilter filter) {

        return ResponseEntity.ok(
                productService.searchProducts(filter)
        );
    }


    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id) {

        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{productId}/validate")
    public ResponseEntity<Boolean> validateProduct(@PathVariable String productId)
    {
        return ResponseEntity.ok(productService.existsByProductId(productId));
    }

    @PutMapping("/{id}/reduce-stock")
    public ResponseEntity<Void> reduceStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {

        productService.reduceStock(id, quantity);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/reservations")
    public ResponseEntity<StockReservation> reserveStock(
            @RequestBody StockReservationRequest request) {

        StockReservation reservation = productService.reserveStock(
                request.getReservationId(),
                request.getOrderId(),
                request.getProductId(),
                request.getQuantity()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

    @PutMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<StockReservation> confirmStock(
            @PathVariable String reservationId) {

        return ResponseEntity.ok(
                productService.confirmStock(reservationId)
        );
    }

    @PutMapping("/reservations/{reservationId}/release")
    public ResponseEntity<StockReservation> releaseStock(
            @PathVariable String reservationId) {

        return ResponseEntity.ok(
                productService.releaseStock(reservationId)
        );
    }

    @PutMapping("/reservations/{reservationId}/compensate")
    public ResponseEntity<CompensationResult> compensateStock(
            @PathVariable String reservationId) {

        CompensationResult result =
                productService.compensateStock(reservationId);

        return ResponseEntity.ok(result);
    }

}
