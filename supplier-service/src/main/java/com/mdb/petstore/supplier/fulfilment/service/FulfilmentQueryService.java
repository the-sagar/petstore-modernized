package com.mdb.petstore.supplier.fulfilment.service;

import java.util.List;
import com.mdb.petstore.supplier.fulfilment.dto.FulfilmentOrderResponse;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import com.mdb.petstore.supplier.fulfilment.repository.SupplierOrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FulfilmentQueryService {
    private static final Sort ORDERING = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("orderId"));
    private final SupplierOrderRepository orders;
    public FulfilmentQueryService(SupplierOrderRepository orders) { this.orders = orders; }

    public List<FulfilmentOrderResponse> list(SupplierOrder.Status status) {
        return (status == null ? orders.findAll(ORDERING) : orders.findByStatus(status, ORDERING))
                .stream().map(this::response).toList();
    }

    public FulfilmentOrderResponse get(String orderId) {
        return response(orders.findById(orderId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier order not found")));
    }

    private FulfilmentOrderResponse response(SupplierOrder order) {
        var lines = order.lines().stream().map(line -> new FulfilmentOrderResponse.Line(line.lineNumber(),
                line.itemId(), line.quantityRequested(), line.quantityShipped(),
                line.quantityRequested() - line.quantityShipped())).toList();
        var shipments = order.shipments().stream().map(shipment -> new FulfilmentOrderResponse.Shipment(
                shipment.eventId(), shipment.createdAt(), shipment.complete(), shipment.shippedLines().stream()
                .map(line -> new FulfilmentOrderResponse.ShippedLine(line.lineNumber(), line.itemId(), line.quantity())).toList())).toList();
        return new FulfilmentOrderResponse(order.orderId(), order.status().name(), order.createdAt(), order.updatedAt(),
                lines.size(), lines.stream().filter(line -> line.remainingQuantity() == 0).count(), lines, shipments);
    }
}
