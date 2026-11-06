
package com.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StockReservationRequest {
    private String reservationId;
    private Long orderId;
    private Long productId;
    private Integer quantity;
}
