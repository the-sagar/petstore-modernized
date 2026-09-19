package com.mdb.petstore.orderprocessing.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import com.mdb.petstore.orderprocessing.order.dto.CreateOrderRequest;
import com.mdb.petstore.orderprocessing.order.dto.OrderResponse;
import com.mdb.petstore.orderprocessing.order.messaging.OrderSubmittedPublisher;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;

import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository repository;

    private final OrderSubmittedPublisher publisher;

    public OrderService(OrderRepository repository, OrderSubmittedPublisher publisher) {
        this.publisher = publisher;
        this.repository = repository;
    }

    public OrderResponse create(CreateOrderRequest request) {
        String id = UUID.randomUUID().toString();
        log.info("Order creation started orderId={} customerId={} lineCount={}",
                id, request.customerId(), request.lineItems().size());
        var lineNumbers = new HashSet<Integer>();
        BigDecimal total = BigDecimal.ZERO;
        for (var line : request.lineItems()) {
            if (!lineNumbers.add(line.lineNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate line number");
            }
            requireRepresentable(line.unitPrice());
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        requireRepresentable(total);
        var order = new Order(id, request.customerId(), request.username(), request.email(), Instant.now(),
                request.locale(), OrderStatus.PENDING, request.billingInfo(), request.shippingInfo(),
                request.payment(), request.lineItems(), total);
        repository.insert(order);
        // If send fails, the PENDING order remains persisted and the request fails.
        // Mongo and JMS are not atomic: an outbox/reconciliation is future hardening.
        publisher.publish(id);
        log.info("Order creation completed orderId={} customerId={} lineCount={} status={}",
                id, order.customerId(), order.lineItems().size(), order.status());
        return new OrderResponse(order.id(), order.status(), order.createdAt(), order.totalPrice());
    }

    private void requireRepresentable(BigDecimal amount) {
        try {
            new Decimal128(amount);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds supported decimal precision");
        }
    }
}
