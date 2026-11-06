
package com.ecommerce.product.repository;

import com.ecommerce.product.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface StockReservationRepository
        extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByReservationId(String reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT r FROM StockReservation r
       WHERE r.reservationId = :reservationId
       """)
    Optional<StockReservation> findByReservationIdForUpdate(
            @Param("reservationId") String reservationId);
}
