package com.mdb.petstore.supplier.messaging;

import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder.Shipment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryFulfilledPublisher {
    private static final Logger log = LoggerFactory.getLogger(InventoryFulfilledPublisher.class);
    private final JmsTemplate jms;
    private final ObjectMapper mapper;
    private final String destination;

    public InventoryFulfilledPublisher(JmsTemplate jms, ObjectMapper mapper,
            @Value("${petstore.inventory.fulfilled-destination}") String destination) {
        this.jms = jms;
        this.mapper = mapper;
        this.destination = destination;
    }

    public void publish(String orderId, Shipment shipment) {
        var lines = shipment.shippedLines().stream()
                .map(l -> new InventoryFulfilled.Line(l.lineNumber(), l.itemId(), l.quantity())).toList();
        jms.convertAndSend(destination, mapper.writeValueAsString(
                new InventoryFulfilled(shipment.eventId(), orderId, lines, shipment.complete())));
        log.info("InventoryFulfilled published orderId={} eventId={} complete={}", orderId, shipment.eventId(), shipment.complete());
    }
}
