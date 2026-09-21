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
    private final org.springframework.context.MessageSource messages;

    public NotificationListener(OrderRepository orders, ObjectMapper mapper, Validator validator,
            JavaMailSender mail, @Value("${petstore.notification.from}") String from,
            org.springframework.context.MessageSource messages) {
        this.orders = orders;
        this.mapper = mapper;
        this.validator = validator;
        this.mail = mail;
        this.from = from;
        this.messages = messages;
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
        java.util.Locale locale = emailLocale(order.locale());
        String subjectKey = switch (event.notificationType()) {
            case ORDER_APPROVED, ORDER_DENIED -> "mail.status.subject";
            case ORDER_SHIPPED -> "mail.shipped.subject";
            case ORDER_COMPLETED -> "mail.completed.subject";
        };
        String subject = messages.getMessage(subjectKey, new Object[]{order.id()}, locale);
        String body = messages.getMessage("mail." + event.notificationType().name() + ".body",
                new Object[]{order.id()}, locale);
        if (event.notificationType() == NotificationType.ORDER_SHIPPED && event.shipmentEventId() != null) {
            body += messages.getMessage("mail.shipmentPass", new Object[]{event.shipmentEventId()}, locale);
        }
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

    private static java.util.Locale emailLocale(String value) {
        String tag = value == null ? "" : value.strip().replace('_', '-');
        return switch (tag.toLowerCase(java.util.Locale.ROOT)) {
            case "ja-jp" -> java.util.Locale.JAPAN;
            case "zh-cn" -> java.util.Locale.SIMPLIFIED_CHINESE;
            default -> java.util.Locale.US;
        };
    }
}
