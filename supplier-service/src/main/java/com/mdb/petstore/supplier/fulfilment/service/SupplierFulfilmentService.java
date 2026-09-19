package com.mdb.petstore.supplier.fulfilment.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder.*;
import com.mdb.petstore.supplier.fulfilment.repository.SupplierOrderRepository;
import com.mdb.petstore.supplier.inventory.model.Inventory;
import com.mdb.petstore.supplier.messaging.InventoryRequested;
import com.mdb.petstore.supplier.messaging.InventoryFulfilledPublisher;
import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SupplierFulfilmentService {
    private static final Logger log = LoggerFactory.getLogger(SupplierFulfilmentService.class);
    private final SupplierOrderRepository orders;
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;
    private final InventoryFulfilledPublisher publisher;

    public SupplierFulfilmentService(SupplierOrderRepository orders, MongoTemplate mongo,
            TransactionTemplate transactions, InventoryFulfilledPublisher publisher) {
        this.orders = orders;
        this.mongo = mongo;
        this.transactions = transactions;
        this.publisher = publisher;
    }

    public void receive(InventoryRequested request) {
        var lines = request.lines().stream().sorted(java.util.Comparator.comparingInt(InventoryRequested.Line::lineNumber))
                .map(l -> new Line(l.lineNumber(), l.itemId(), l.quantity(), 0)).toList();
        var existing = orders.findById(request.orderId()).orElse(null);
        if (existing == null) {
            var now = Instant.now();
            try {
                orders.insert(new SupplierOrder(request.orderId(), Status.PENDING, lines, List.of(), now, now, null));
            } catch (DuplicateKeyException duplicate) {
                // Another delivery inserted this order; compare its immutable request below.
            }
            existing = orders.findById(request.orderId()).orElseThrow();
        }
        var original = existing.lines().stream()
                .map(l -> new Line(l.lineNumber(), l.itemId(), l.quantityRequested(), 0)).toList();
        if (!original.equals(lines)) {
            log.warn("Conflicting InventoryRequested ignored orderId={}", request.orderId());
            return;
        }
        fulfil(request.orderId());
    }

    public void retryPending() {
        for (var order : orders.findByStatusOrderByCreatedAtAscOrderIdAsc(Status.PENDING)) fulfil(order.orderId());
    }

    public void fulfil(String orderId) {
        Shipment shipment = null;
        for (int attempt = 0; ; attempt++) {
            try {
                shipment = transactions.execute(status -> allocate(orderId));
                break;
            } catch (RuntimeException failure) {
                if (attempt >= 4 || !transientConflict(failure)) throw failure;
                // Retry only aborted Mongo transactions, never a JMS send or unknown commit outcome.
            }
        }
        if (shipment != null) {
            log.info("Supplier fulfilment committed orderId={} eventId={} lineCount={} complete={}",
                    orderId, shipment.eventId(), shipment.shippedLines().size(), shipment.complete());
            // Transaction already committed. Send failure preserves stock/state/history for reconciliation.
            publisher.publish(orderId, shipment);
        }
    }

    private Shipment allocate(String orderId) {
        var order = orders.findById(orderId).orElseThrow();
        if (order.status() == Status.COMPLETED) return null;
        var lines = new ArrayList<Line>();
        var shipped = new ArrayList<ShippedLine>();
        var now = Instant.now();
        for (var line : order.lines()) {
            int remaining = line.quantityRequested() - line.quantityShipped();
            if (remaining > 0) {
                var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(line.itemId())
                                .and("quantity").gte(remaining)),
                        new Update().inc("quantity", -remaining).set("updatedAt", now), Inventory.class);
                if (result.getModifiedCount() == 1) {
                    shipped.add(new ShippedLine(line.lineNumber(), line.itemId(), remaining));
                    line = new Line(line.lineNumber(), line.itemId(), line.quantityRequested(), line.quantityRequested());
                }
            }
            lines.add(line);
        }
        if (shipped.isEmpty()) return null;
        boolean complete = lines.stream().allMatch(l -> l.quantityRequested() == l.quantityShipped());
        var shipment = new Shipment(UUID.randomUUID().toString(), List.copyOf(shipped), complete, now);
        var history = new ArrayList<>(order.shipments());
        history.add(shipment);
        orders.save(new SupplierOrder(orderId, complete ? Status.COMPLETED : Status.PENDING,
                lines, history, order.createdAt(), now, order.version()));
        return shipment;
    }

    private boolean transientConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoFailure && mongoFailure.hasErrorLabel("UnknownTransactionCommitResult")) return false;
            if (cause instanceof MongoException mongoFailure && mongoFailure.hasErrorLabel("TransientTransactionError")) return true;
            if (cause instanceof OptimisticLockingFailureException) return true;
        }
        return false;
    }
}
