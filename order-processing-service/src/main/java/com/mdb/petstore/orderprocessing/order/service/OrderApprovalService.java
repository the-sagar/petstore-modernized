package com.mdb.petstore.orderprocessing.order.service;

import com.mdb.petstore.orderprocessing.order.messaging.InventoryRequestedPublisher;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Shared approval boundary for automatic approval and authorized manual approval. */
@Service
public class OrderApprovalService {
    private static final Logger log = LoggerFactory.getLogger(OrderApprovalService.class);
    private final MongoTemplate mongo;
    private final InventoryRequestedPublisher publisher;

    public OrderApprovalService(MongoTemplate mongo, InventoryRequestedPublisher publisher) {
        this.mongo = mongo;
        this.publisher = publisher;
    }

    public boolean approve(Order order) {
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(order.id())
                        .and("status").is(OrderStatus.PENDING)), Update.update("status", OrderStatus.APPROVED), Order.class);
        if (result.getModifiedCount() != 1) return false;
        log.info("Order approved orderId={} locale={} total={}", order.id(), order.locale(), order.totalPrice());
        // No distributed transaction: a failed send leaves APPROVED and requires replay/reconciliation.
        publisher.publish(order);
        return true;
    }
}
