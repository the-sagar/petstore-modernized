package com.mdb.petstore.orderprocessing.order.messaging;

import com.mdb.petstore.orderprocessing.order.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryRequestedPublisher {
    private static final Logger log = LoggerFactory.getLogger(InventoryRequestedPublisher.class);
    private final JmsTemplate jms;
    private final ObjectMapper mapper;
    private final String destination;

    public InventoryRequestedPublisher(JmsTemplate jms, ObjectMapper mapper,
            @Value("${petstore.inventory.requested-destination}") String destination) {
        this.jms = jms;
        this.mapper = mapper;
        this.destination = destination;
    }

    public void publish(Order order) {
        var lines = order.lineItems().stream()
                .map(line -> new InventoryRequested.Line(line.lineNumber(), line.itemId(), line.quantity())).toList();
        jms.convertAndSend(destination, mapper.writeValueAsString(new InventoryRequested(order.id(), lines)));
        log.info("InventoryRequested published orderId={} lineCount={}", order.id(), lines.size());
    }
}
