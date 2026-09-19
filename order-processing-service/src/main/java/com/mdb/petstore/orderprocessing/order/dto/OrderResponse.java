package com.mdb.petstore.orderprocessing.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;

public record OrderResponse(String orderId, OrderStatus status, Instant createdAt, BigDecimal totalPrice) {
}
