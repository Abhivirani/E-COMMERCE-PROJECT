package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.model.CompensationResult;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ecommerce.product.model.StockReservation;
import com.ecommerce.product.model.ReservationStatus;
import com.ecommerce.product.repository.StockReservationRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import com.ecommerce.product.dto.ProductSearchFilter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    public final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;

    public List<ProductResponse> getAllProduct() {
        return productRepository.findByActiveTrue().stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.searchProducts(keyword).stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }


    public List<ProductResponse> searchProducts(ProductSearchFilter filter) {

        if (filter == null) {
            throw new IllegalArgumentException("Search filter cannot be null");
        }


        if (filter.minPrice() != null
                && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new IllegalArgumentException(
                    "Minimum price cannot be greater than maximum price");
        }

        Specification<Product> specification =
                ProductSpecification.buildSpecification(
                        filter.keyword(),
                        filter.category(),
                        filter.minPrice(),
                        filter.maxPrice(),
                        filter.inStock(),
                        filter.active()
                );

        return productRepository.findAll(specification)
                .stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = new Product();

        updateProductFromRequest(product, productRequest);

        product.setStockQuantity(productRequest.getStockQuantity());

        Product savedProduct = productRepository.save(product);

        return mapToProductResponse(savedProduct);
    }


    public Optional<ProductResponse> updateProduct(
            Long id,
            ProductRequest productRequest) {

        return productRepository.findById(id)
                .map(existingProduct -> {
                    updateProductFromRequest(existingProduct, productRequest);

                    Product savedProduct = productRepository.save(existingProduct);

                    return mapToProductResponse(savedProduct);
                });
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                        .orElseThrow(()-> new RuntimeException("Product Not Found"));
        product.setActive(false);
        productRepository.save(product);
    }

    private ProductResponse mapToProductResponse(Product savedProduct) {
        ProductResponse response = new ProductResponse();
        response.setId(savedProduct.getId());
        response.setName(savedProduct.getName());
        response.setActive(savedProduct.getActive());
        response.setCategory(savedProduct.getCategory());
        response.setDescription(savedProduct.getDescription());
        response.setPrice(savedProduct.getPrice());
        response.setImageUrl(savedProduct.getImageUrl());
        response.setStockQuantity(savedProduct.getStockQuantity());
        return response;
    }

    private void updateProductFromRequest(
            Product product,
            ProductRequest productRequest) {

        if (productRequest.getName() != null) {
            product.setName(productRequest.getName());
        }

        if (productRequest.getCategory() != null) {
            product.setCategory(productRequest.getCategory());
        }

        if (productRequest.getDescription() != null) {
            product.setDescription(productRequest.getDescription());
        }

        if (productRequest.getPrice() != null) {
            product.setPrice(productRequest.getPrice());
        }

        if (productRequest.getImageUrl() != null) {
            product.setImageUrl(productRequest.getImageUrl());
        }

        // Do not modify stockQuantity here.
        // Stock is managed separately through inventory operations.
    }


    public Boolean existsByProductId(String productId) {
        return productRepository.existsById(productId);
    }

    public Optional<ProductResponse> getProductById(Long id) {
        return productRepository.findById(id)
                .map(this::mapToProductResponse);
    }


    @Transactional
    public void reduceStock(Long id, Integer quantity) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Product ID must be positive");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found: " + id));

        Integer currentStock = product.getStockQuantity();

        if (currentStock == null) {
            throw new IllegalStateException(
                    "Stock quantity is not initialized for product: " + id);
        }

        if (currentStock < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for product: " + id);
        }

        int updatedStock = currentStock - quantity;

        product.setStockQuantity(updatedStock);
        productRepository.save(product);

        log.info(
                "STOCK_REDUCED: productId={}, previousStock={}, quantity={}, updatedStock={}",
                id, currentStock, quantity, updatedStock);
    }



    @Transactional
    public StockReservation reserveStock(
            String reservationId,
            Long orderId,
            Long productId,
            Integer quantity) {

        log.info(
                "RESERVE_START: reservationId={}, orderId={}, productId={}, quantity={}",
                reservationId, orderId, productId, quantity
        );

        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("Reservation ID is required");
        }

        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        var existing = stockReservationRepository
                .findByReservationId(reservationId);

        if (existing.isPresent()) {
            StockReservation reservation = existing.get();

            if (!reservation.getOrderId().equals(orderId)
                    || !reservation.getProductId().equals(productId)
                    || !reservation.getQuantity().equals(quantity)) {
                throw new IllegalStateException(
                        "Reservation ID already used with different details");
            }

            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                throw new IllegalStateException(
                        "Released reservation cannot be reused; create a new reservation ID");
            }

            log.warn(
                    "RESERVATION_ALREADY_EXISTS: reservationId={}, status={}, stock deduction skipped",
                    reservationId, reservation.getStatus()
            );

            return reservation;
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() ->
                        new RuntimeException("Product not found: " + productId));

        Integer currentStock = product.getStockQuantity();

        if (currentStock == null) {
            throw new IllegalStateException(
                    "Stock quantity is not initialized for product: " + productId);
        }

        log.info(
                "STOCK_BEFORE_RESERVATION: productId={}, stock={}",
                productId, currentStock
        );

        if (currentStock < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for product: " + productId);
        }

        int updatedStock = currentStock - quantity;
        product.setStockQuantity(updatedStock);
        productRepository.save(product);

        log.info(
                "STOCK_AFTER_RESERVATION: productId={}, previousStock={}, quantity={}, updatedStock={}",
                productId, currentStock, quantity, updatedStock
        );

        log.info(
                "STOCK_AFTER_RESERVATION: productId={}, stock={}",
                productId, product.getStockQuantity()
        );

        StockReservation reservation = new StockReservation();
        reservation.setReservationId(reservationId);
        reservation.setOrderId(orderId);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.RESERVED);


        log.info(
                "RESERVATION_CREATED: reservationId={}, status={}",
                reservation.getReservationId(), reservation.getStatus()
        );
        return stockReservationRepository.save(reservation);
    }

    @Transactional
    public StockReservation releaseStock(String reservationId) {

        StockReservation reservation = stockReservationRepository
                .findByReservationIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Stock reservation not found: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return reservation;
        }

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Confirmed stock reservation cannot be released");
        }

        Product product = productRepository.findByIdForUpdate(
                        reservation.getProductId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found: " + reservation.getProductId()));

        Integer currentStock = product.getStockQuantity();

        if (currentStock == null) {
            throw new IllegalStateException(
                    "Stock quantity is not initialized for product: "
                            + reservation.getProductId());
        }

        int restoredStock = currentStock + reservation.getQuantity();

        product.setStockQuantity(restoredStock);
        productRepository.save(product);

        log.info(
                "STOCK_RELEASED: reservationId={}, productId={}, previousStock={}, restoredQuantity={}, updatedStock={}",
                reservationId,
                reservation.getProductId(),
                currentStock,
                reservation.getQuantity(),
                restoredStock
        );

        reservation.setStatus(ReservationStatus.RELEASED);
        return stockReservationRepository.save(reservation);
    }


    @Transactional
    public CompensationResult compensateStock(String reservationId) {

        Optional<StockReservation> reservationOptional =
                stockReservationRepository
                        .findByReservationIdForUpdate(reservationId);

        if (reservationOptional.isEmpty()) {
            log.warn(
                    "Compensation could not verify reservation {}: reservation not found",
                    reservationId
            );

            return CompensationResult.NOT_FOUND;
        }

        StockReservation reservation = reservationOptional.get();

        // Prevent restoring stock more than once.
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return CompensationResult.ALREADY_RELEASED;
        }

        Product product = productRepository.findByIdForUpdate(
                        reservation.getProductId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Product not found: " + reservation.getProductId()));

        // Restore stock for RESERVED or CONFIRMED reservations.
        Integer currentStock = product.getStockQuantity();

        if (currentStock == null) {
            throw new IllegalStateException(
                    "Stock quantity is not initialized for product: "
                            + reservation.getProductId());
        }

        int restoredStock = currentStock + reservation.getQuantity();

        product.setStockQuantity(restoredStock);
        productRepository.save(product);

        log.info(
                "STOCK_COMPENSATED: reservationId={}, productId={}, previousStock={}, restoredQuantity={}, updatedStock={}",
                reservationId,
                reservation.getProductId(),
                currentStock,
                reservation.getQuantity(),
                restoredStock
        );

        reservation.setStatus(ReservationStatus.RELEASED);
        stockReservationRepository.save(reservation);

        return CompensationResult.RELEASED;
    }


    @Transactional
    public StockReservation confirmStock(String reservationId) {

        log.info("CONFIRM_START: reservationId={}", reservationId);

        StockReservation reservation = stockReservationRepository
                .findByReservationIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Stock reservation not found: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            log.info(
                    "RESERVATION_ALREADY_CONFIRMED: reservationId={}",
                    reservationId
            );
            return reservation;
        }

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw new IllegalStateException(
                    "Released stock reservation cannot be confirmed");
        }
        log.info(
                "RESERVATION_CONFIRMING: reservationId={}, previousStatus={}",
                reservationId, reservation.getStatus()
        );
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return stockReservationRepository.save(reservation);
    }

}
