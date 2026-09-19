package com.mdb.petstore.checkout.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(String orderId, String status, Instant createdAt, BigDecimal totalPrice) {
}
