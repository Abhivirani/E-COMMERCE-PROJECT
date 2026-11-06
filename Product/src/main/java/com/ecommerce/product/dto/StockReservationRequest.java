
package com.ecommerce.product.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockReservationRequest {

    private String reservationId;
    private Long orderId;
    private Long productId;
    private Integer quantity;
}
