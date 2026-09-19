package com.mdb.petstore.orderprocessing.order.messaging;

import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import com.mdb.petstore.orderprocessing.order.service.ApprovalPolicy;
import com.mdb.petstore.orderprocessing.order.service.OrderApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderSubmittedListener {
    private static final Logger log = LoggerFactory.getLogger(OrderSubmittedListener.class);
    private final OrderRepository orders;
    private final OrderApprovalService approval;
    private final ApprovalPolicy policy;
    private final ObjectMapper mapper;

    public OrderSubmittedListener(OrderRepository orders, OrderApprovalService approval, ApprovalPolicy policy,
            ObjectMapper mapper) {
        this.orders = orders;
        this.approval = approval;
        this.policy = policy;
        this.mapper = mapper;
    }

    @JmsListener(destination = "${petstore.order.submitted-destination}")
    public void receive(String json) {
        OrderSubmitted event;
        try {
            event = json == null ? null : mapper.readValue(json, OrderSubmitted.class);
        } catch (JacksonException exception) {
            log.warn("Invalid OrderSubmitted JSON ignored");
            return;
        }
        if (event == null || event.orderId() == null || event.orderId().isBlank()) {
            log.warn("OrderSubmitted with missing orderId ignored");
            return;
        }
        String id = event.orderId();
        log.info("OrderSubmitted event received orderId={}", id);
        var order = orders.findById(id).orElse(null);
        if (order == null) {
            log.warn("OrderSubmitted references unknown orderId={}", id);
            return;
        }
        if (order.status() != OrderStatus.PENDING) {
            log.debug("OrderSubmitted already handled orderId={} status={}", id, order.status());
            return;
        }
        if (!policy.shouldAutomaticallyApprove(order.locale(), order.totalPrice())) {
            log.info("Order retained pending orderId={} locale={} total={}", id, order.locale(), order.totalPrice());
            return;
        }
        approval.approve(order);
    }
}
