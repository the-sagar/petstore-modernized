package com.mdb.petstore.orderprocessing.order.messaging;

import com.mdb.petstore.orderprocessing.order.notification.NotificationPublisher;
import com.mdb.petstore.orderprocessing.order.notification.NotificationType;
import java.util.ArrayList;
import java.util.HashSet;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryFulfilledListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryFulfilledListener.class);
    private final OrderRepository orders;
    private final MongoTemplate mongo;
    private final NotificationPublisher notifications;
    private final ObjectMapper mapper;
    private final Validator validator;

    public InventoryFulfilledListener(OrderRepository orders, MongoTemplate mongo, ObjectMapper mapper, Validator validator, NotificationPublisher notifications) {
        this.orders = orders;
        this.mongo = mongo;
        this.notifications = notifications;
        this.mapper = mapper;
        this.validator = validator;
    }

    @JmsListener(destination = "${petstore.inventory.fulfilled-destination}")
    public void receive(String json) {
        InventoryFulfilled event;
        try {
            event = json == null ? null : mapper.readValue(json, InventoryFulfilled.class);
        } catch (JacksonException exception) {
            log.warn("Malformed InventoryFulfilled ignored");
            return;
        }
        if (event == null || !validator.validate(event).isEmpty()) {
            log.warn("Invalid InventoryFulfilled ignored");
            return;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            var order = orders.findById(event.orderId()).orElse(null);
            if (order == null) {
                log.warn("InventoryFulfilled references unknown orderId={}", event.orderId());
                return;
            }
            if (order.fulfilmentEventIds().contains(event.eventId()) ||
                    (order.status() != OrderStatus.APPROVED && order.status() != OrderStatus.SHIPPED_PART)) return;
            var lines = new ArrayList<>(order.lineItems());
            var seen = new HashSet<Integer>();
            for (var shipped : event.shippedLines()) {
                int index = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).lineNumber() == shipped.lineNumber()) index = i;
                }
                if (index < 0 || !seen.add(shipped.lineNumber())) {
                    log.warn("InventoryFulfilled contains invalid lines orderId={}", order.id());
                    return;
                }
                var line = lines.get(index);
                if (!line.itemId().equals(shipped.itemId()) || shipped.quantity() > line.quantity() - line.quantityShipped()) {
                    log.warn("InventoryFulfilled contains invalid item or quantity orderId={}", order.id());
                    return;
                }
                lines.set(index, line.withQuantityShipped(line.quantityShipped() + shipped.quantity()));
            }
            // Derive status from authoritative cumulative quantities, never trust the complete hint.
            var status = lines.stream().allMatch(l -> l.quantityShipped() == l.quantity())
                    ? OrderStatus.COMPLETED : OrderStatus.SHIPPED_PART;
            var query = Query.query(Criteria.where("_id").is(order.id()).and("version").is(order.version())
                    .and("status").is(order.status()).and("fulfilmentEventIds").ne(event.eventId()));
            var update = Update.update("lineItems", lines).set("status", status)
                    .addToSet("fulfilmentEventIds", event.eventId());
            // MongoTemplate increments @Version together with quantities, status, and the receipt.
            if (mongo.updateFirst(query, update, Order.class).getModifiedCount() == 1) {
                log.info("Order fulfilment applied orderId={} eventId={} status={}", order.id(), event.eventId(), status);
                notifications.publish(order.id(), NotificationType.ORDER_SHIPPED, event.eventId());
                if (status == OrderStatus.COMPLETED) {
                    notifications.publish(order.id(), NotificationType.ORDER_COMPLETED, event.eventId());
                }
                return;
            }
        }
        throw new OptimisticLockingFailureException("Concurrent fulfilment update; redeliver event");
    }
}
