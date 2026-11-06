package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ReservationStatus;
import com.ecommerce.product.model.StockReservation;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ecommerce.product.model.CompensationResult;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceReservationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 123L;
    private static final String RESERVATION_ID = "order-123-product-1";
    private static final Integer QUANTITY = 2;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Wireless Mouse");
        product.setStockQuantity(10);
    }

    // 1. A new reservation decreases stock and creates a reservation.
    @Test
    void shouldReserveStockSuccessfully() {
        when(stockReservationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.empty());

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(stockReservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockReservation result = productService.reserveStock(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY
        );

        assertNotNull(result);
        assertEquals(RESERVATION_ID, result.getReservationId());
        assertEquals(ORDER_ID, result.getOrderId());
        assertEquals(PRODUCT_ID, result.getProductId());
        assertEquals(QUANTITY, result.getQuantity());
        assertEquals(ReservationStatus.RESERVED, result.getStatus());

        assertEquals(8, product.getStockQuantity());

        verify(productRepository).findByIdForUpdate(PRODUCT_ID);
        verify(productRepository).save(product);
        verify(stockReservationRepository).save(any(StockReservation.class));
    }

    // 2. Repeating an identical reservation must not decrease stock again.
    @Test
    void shouldReturnExistingReservationWithoutDeductingStockAgain() {
        StockReservation existing = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RESERVED
        );

        when(stockReservationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(existing));

        StockReservation result = productService.reserveStock(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY
        );

        assertSame(existing, result);
        assertEquals(10, product.getStockQuantity());

        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(productRepository, never()).save(any(Product.class));
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    // 3. The same reservation ID cannot be reused with different details.
    @Test
    void shouldRejectReservationIdWithDifferentDetails() {
        StockReservation existing = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RESERVED
        );

        when(stockReservationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> productService.reserveStock(
                        RESERVATION_ID, ORDER_ID, PRODUCT_ID, 5
                )
        );

        verify(productRepository, never()).findByIdForUpdate(anyLong());
    }

    // 4. A released reservation ID cannot be reused.
    @Test
    void shouldRejectReuseOfReleasedReservation() {
        StockReservation existing = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RELEASED
        );

        when(stockReservationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> productService.reserveStock(
                        RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY
                )
        );

        verify(productRepository, never()).findByIdForUpdate(anyLong());
    }

    // 5. A reservation must fail when available stock is insufficient.
    @Test
    void shouldRejectReservationWhenStockIsInsufficient() {
        product.setStockQuantity(1);

        when(stockReservationRepository.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.empty());

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                RuntimeException.class,
                () -> productService.reserveStock(
                        RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY
                )
        );

        assertEquals(1, product.getStockQuantity());

        verify(productRepository, never()).save(any(Product.class));
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    // 6. Invalid reservation input must be rejected.
    @Test
    void shouldRejectInvalidReservationInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> productService.reserveStock(
                        " ", ORDER_ID, PRODUCT_ID, QUANTITY
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> productService.reserveStock(
                        RESERVATION_ID, 0L, PRODUCT_ID, QUANTITY
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> productService.reserveStock(
                        RESERVATION_ID, ORDER_ID, PRODUCT_ID, 0
                )
        );

        verifyNoInteractions(productRepository);
        verifyNoInteractions(stockReservationRepository);
    }

    // 7. Releasing a reserved item restores its stock.
    @Test
    void shouldReleaseReservationAndRestoreStock() {
        product.setStockQuantity(8);

        StockReservation reservation = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RESERVED
        );

        when(stockReservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(stockReservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockReservation result = productService.releaseStock(RESERVATION_ID);

        assertEquals(10, product.getStockQuantity());
        assertEquals(ReservationStatus.RELEASED, result.getStatus());

        verify(productRepository).save(product);
        verify(stockReservationRepository).save(reservation);
    }

    // 8. Releasing an already released reservation is idempotent.
    @Test
    void shouldNotRestoreStockTwiceWhenReservationIsAlreadyReleased() {
        StockReservation reservation = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RELEASED
        );

        when(stockReservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));

        StockReservation result = productService.releaseStock(RESERVATION_ID);

        assertSame(reservation, result);

        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(productRepository, never()).save(any(Product.class));
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    // 9. Confirming a reservation changes its status to CONFIRMED.
    @Test
    void shouldConfirmReservation() {
        StockReservation reservation = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RESERVED
        );

        when(stockReservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));

        when(stockReservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockReservation result = productService.confirmStock(RESERVATION_ID);

        assertEquals(ReservationStatus.CONFIRMED, result.getStatus());

        verify(productRepository, never()).save(any(Product.class));
        verify(stockReservationRepository).save(reservation);
    }

    // 10. Confirmed stock cannot be released through the release operation.
    @Test
    void shouldRejectReleaseOfConfirmedReservation() {
        StockReservation reservation = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.CONFIRMED
        );

        when(stockReservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));

        assertThrows(
                IllegalStateException.class,
                () -> productService.releaseStock(RESERVATION_ID)
        );

        verify(productRepository, never()).save(any(Product.class));
    }

    // 11. Compensation restores stock for a confirmed reservation.
    @Test
    void shouldCompensateConfirmedReservationAndRestoreStock() {
        product.setStockQuantity(8);

        StockReservation reservation = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.CONFIRMED
        );

        when(stockReservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(stockReservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompensationResult result = productService.compensateStock(RESERVATION_ID);

        assertEquals(10, product.getStockQuantity());
        assertEquals(CompensationResult.RELEASED, result);

        verify(productRepository).save(product);
        verify(stockReservationRepository).save(reservation);
    }

    // 12. Repeating compensation must not restore stock a second time.
    @Test
    void shouldNotRestoreStockTwiceDuringCompensation() {
        StockReservation reservation = createReservation(
                RESERVATION_ID, ORDER_ID, PRODUCT_ID, QUANTITY,
                ReservationStatus.RELEASED
        );

        when(stockReservationRepository.findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));

        CompensationResult result = productService.compensateStock(RESERVATION_ID);

        assertEquals(CompensationResult.ALREADY_RELEASED, result);

        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(productRepository, never()).save(any(Product.class));
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }

    private StockReservation createReservation(
            String reservationId,
            Long orderId,
            Long productId,
            Integer quantity,
            ReservationStatus status
    ) {
        StockReservation reservation = new StockReservation();
        reservation.setReservationId(reservationId);
        reservation.setOrderId(orderId);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        return reservation;
    }

    @Test
    void shouldReturnNotFoundWhenCompensatingMissingReservation() {
        when(stockReservationRepository
                .findByReservationIdForUpdate(RESERVATION_ID))
                .thenReturn(Optional.empty());

        CompensationResult result =
                productService.compensateStock(RESERVATION_ID);

        assertEquals(CompensationResult.NOT_FOUND, result);

        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(productRepository, never()).save(any(Product.class));
        verify(stockReservationRepository, never()).save(any(StockReservation.class));
    }
}