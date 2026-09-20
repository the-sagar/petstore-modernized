package com.mdb.petstore.orderprocessing.order.notification;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private final JmsTemplate jms;
    private final ObjectMapper mapper;
    private final String destination;
    private final boolean enabled;

    public NotificationPublisher(JmsTemplate jms, ObjectMapper mapper,
            @Value("${petstore.notification.requested-destination}") String destination,
            @Value("${petstore.notification.enabled:false}") boolean enabled) {
        this.jms = jms;
        this.mapper = mapper;
        this.destination = destination;
        this.enabled = enabled;
    }

    public void publish(String orderId, NotificationType type, String shipmentEventId) {
        if (!enabled) return;
        var event = new NotificationRequested(UUID.randomUUID().toString(), orderId, type, shipmentEventId);
        try {
            jms.convertAndSend(destination, mapper.writeValueAsString(event));
            log.info("Notification requested orderId={} type={}", orderId, type);
        } catch (JmsException | JacksonException exception) {
            // Best-effort: Mongo and JMS are not atomic. Do not undo the business transition.
            // Exception messages can contain broker credentials; do not log them.
            log.warn("Notification request failed orderId={} type={}", orderId, type);
        }
    }
}
