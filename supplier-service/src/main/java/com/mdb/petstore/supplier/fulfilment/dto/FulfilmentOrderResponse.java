package com.mdb.petstore.supplier.fulfilment.dto;

import java.time.Instant;
import java.util.List;

public record FulfilmentOrderResponse(String orderId, String status, Instant createdAt, Instant updatedAt,
        int requestedLineCount, long shippedLineCount, List<Line> lines, List<Shipment> shipments) {
    public record Line(int lineNumber, String itemId, int quantityRequested, int quantityShipped, int remainingQuantity) {}
    public record Shipment(String eventId, Instant createdAt, boolean complete, List<ShippedLine> shippedLines) {}
    public record ShippedLine(int lineNumber, String itemId, int quantity) {}
}
