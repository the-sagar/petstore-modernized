package com.mdb.petstore.supplier.fulfilment.model;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("supplierOrders")
public record SupplierOrder(@Id String orderId, Status status, List<Line> lines,
        List<Shipment> shipments, Instant createdAt, Instant updatedAt, @Version Long version) {
    public enum Status { PENDING, COMPLETED }
    public record Line(int lineNumber, String itemId, int quantityRequested, int quantityShipped) {}
    // Shipment history is the business record of each pass, not an automatically dispatched outbox.
    public record Shipment(String eventId, List<ShippedLine> shippedLines, boolean complete, Instant createdAt) {}
    public record ShippedLine(int lineNumber, String itemId, int quantity) {}
    public SupplierOrder {
        lines = List.copyOf(lines);
        shipments = List.copyOf(shipments);
    }
}
