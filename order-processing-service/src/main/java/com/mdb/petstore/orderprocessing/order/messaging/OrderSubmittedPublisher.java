package com.mdb.petstore.orderprocessing.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderSubmittedPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderSubmittedPublisher.class);
    private final JmsTemplate jms;
    private final ObjectMapper mapper;
    private final String destination;

    public OrderSubmittedPublisher(JmsTemplate jms, ObjectMapper mapper,
            @Value("${petstore.order.submitted-destination}") String destination) {
        this.jms = jms;
        this.mapper = mapper;
        this.destination = destination;
    }

    public void publish(String orderId) {
        // JSON TextMessage, never Java serialization or the Mongo aggregate.
        jms.convertAndSend(destination, mapper.writeValueAsString(new OrderSubmitted(orderId)));
        log.info("OrderSubmitted event published orderId={}", orderId);
    }
}
