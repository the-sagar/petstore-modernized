package com.mdb.petstore.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderResponse(String orderId, String username, Instant createdAt, String locale,
        AdminOrderStatus status, BigDecimal totalPrice, List<Line> lines) {
    public record Line(int lineNumber, String itemId, String productId, String categoryId,
            int quantity, int quantityShipped, BigDecimal unitPrice) {}
}
