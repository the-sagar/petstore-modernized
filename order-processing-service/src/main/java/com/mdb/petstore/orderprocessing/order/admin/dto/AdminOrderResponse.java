package com.mdb.petstore.orderprocessing.order.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;

public record AdminOrderResponse(String orderId, String username, Instant createdAt, String locale,
        OrderStatus status, BigDecimal totalPrice, List<Line> lines) {
    public record Line(int lineNumber, String itemId, String productId, String categoryId,
            int quantity, int quantityShipped, BigDecimal unitPrice) {}
}
