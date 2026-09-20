package com.mdb.petstore.orderprocessing.order.notification;

import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "petstore.notification.enabled", havingValue = "true")
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final OrderRepository orders;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final JavaMailSender mail;
    private final String from;

    public NotificationListener(OrderRepository orders, ObjectMapper mapper, Validator validator,
            JavaMailSender mail, @Value("${petstore.notification.from}") String from) {
        this.orders = orders;
        this.mapper = mapper;
        this.validator = validator;
        this.mail = mail;
        this.from = from;
    }

    @JmsListener(destination = "${petstore.notification.requested-destination}")
    public void receive(String json) {
        NotificationRequested event;
        try {
            event = json == null ? null : mapper.readValue(json, NotificationRequested.class);
        } catch (JacksonException exception) {
            log.warn("Malformed NotificationRequested ignored");
            return;
        }
        if (event == null || !validator.validate(event).isEmpty()) {
            log.warn("Invalid NotificationRequested ignored");
            return;
        }
        var order = orders.findById(event.orderId()).orElse(null);
        if (order == null || order.email() == null || order.email().isBlank()) {
            log.warn("Notification order or recipient unavailable orderId={} type={}", event.orderId(), event.notificationType());
            return;
        }
        // The aggregate records shipment receipts, not per-pass quantities. Never label cumulative
        // quantities as this shipment's contents; verify the receipt and identify the pass instead.
        if (event.shipmentEventId() != null && !order.fulfilmentEventIds().contains(event.shipmentEventId())) {
            log.warn("Notification shipment receipt unavailable orderId={} type={}", order.id(), event.notificationType());
            return;
        }
        String subject = switch (event.notificationType()) {
            case ORDER_APPROVED, ORDER_DENIED -> "Java Pet Store Order Status: " + order.id();
            case ORDER_SHIPPED -> "Java Pet Store Order Shipped: " + order.id();
            case ORDER_COMPLETED -> "Java Pet Store Order COMPLETED: " + order.id();
        };
        String body = switch (event.notificationType()) {
            case ORDER_APPROVED -> "Order " + order.id() + " has been APPROVED.";
            case ORDER_DENIED -> "Order " + order.id() + " has been DENIED.";
            case ORDER_SHIPPED -> "Items from order " + order.id() + " have shipped."
                    + (event.shipmentEventId() == null ? "" : " Shipment pass: " + event.shipmentEventId() + ".");
            case ORDER_COMPLETED -> "Fulfilment of order " + order.id() + " is complete.";
        };
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(order.email());
        message.setSubject(subject);
        message.setText(body);
        try {
            mail.send(message);
            log.info("Notification email sent orderId={} type={}", order.id(), event.notificationType());
        } catch (MailException exception) {
            // Spring JavaMailSender raises MailException. Acknowledge failed SMTP attempts;
            // delivery is best-effort and never mutates order/inventory state.
            log.warn("Notification email delivery failed orderId={} type={}", order.id(), event.notificationType());
        }
    }
}
