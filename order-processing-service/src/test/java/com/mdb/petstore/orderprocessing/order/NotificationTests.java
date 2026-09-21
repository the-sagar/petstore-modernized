package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.mdb.petstore.orderprocessing.order.model.*;
import com.mdb.petstore.orderprocessing.order.notification.*;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationTests {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final OrderRepository orders = mock(OrderRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final JsonMapper mapper = new JsonMapper();
    private final NotificationListener listener = new NotificationListener(orders, mapper,
            VALIDATORS.getValidator(), mail, "petstore@localhost", messages());

    private static org.springframework.context.MessageSource messages() {
        var source = new org.springframework.context.support.ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void localizesEachNotificationFromOrderSnapshot(NotificationType type) {
        for (String locale : List.of("ja_JP", "zh-CN", "unsupported")) {
            reset(orders, mail);
            when(orders.findById("order-1")).thenReturn(Optional.of(order(OrderStatus.COMPLETED, locale)));
            listener.receive(json(type));
            var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mail).send(sent.capture());
            var message = sent.getValue();
            assertTrue(message.getSubject().contains("order-1"));
            assertTrue(message.getText().contains("order-1"));
            assertTrue(message.getSubject().contains(locale.equals("ja_JP") ? (type == NotificationType.ORDER_SHIPPED ? "商品発送" : "注文") : locale.equals("zh-CN") ? "订单" : "Order"));
            assertTrue(message.getText().toLowerCase(java.util.Locale.ROOT).contains(locale.equals("ja_JP") ? "注文" : locale.equals("zh-CN") ? "订单" : "order"));
            verify(orders).findById("order-1");
            verifyNoMoreInteractions(orders);
        }
    }

    @AfterAll
    static void closeValidators() { VALIDATORS.close(); }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void reloadsSnapshotAndSendsDeterministicSubjectAndSafeBody(NotificationType type) {
        // A queued approval can be delivered after completion; content describes the requested transition.
        when(orders.findById("order-1")).thenReturn(Optional.of(order(OrderStatus.COMPLETED)));
        listener.receive(json(type));
        verify(orders).findById("order-1");
        verifyNoMoreInteractions(orders);
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        var message = sent.getValue();
        assertArrayEquals(new String[]{"snapshot@example.com"}, message.getTo());
        assertEquals("petstore@localhost", message.getFrom());
        String expected = switch (type) {
            case ORDER_APPROVED, ORDER_DENIED -> "Java Pet Store Order Status: order-1";
            case ORDER_SHIPPED -> "Java Pet Store Order Shipped: order-1";
            case ORDER_COMPLETED -> "Java Pet Store Order COMPLETED: order-1";
        };
        assertEquals(expected, message.getSubject());
        String body = message.getText();
        assertNotNull(body);
        assertTrue(body.contains("order-1"));
        switch (type) {
            case ORDER_APPROVED -> assertTrue(body.contains("APPROVED"));
            case ORDER_DENIED -> assertTrue(body.contains("DENIED"));
            case ORDER_SHIPPED -> { assertTrue(body.contains("have shipped")); assertTrue(body.contains("pass-1")); }
            case ORDER_COMPLETED -> assertTrue(body.contains("is complete"));
        }
        for (String secret : List.of("VISA", "9876", "Private Street", "billing@example.com"))
            assertFalse(body.contains(secret));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"APPROVED", "DENIED", "SHIPPED_PART", "COMPLETED"})
    void smtpFailureIsAcknowledgedWithoutWritingBusinessState(OrderStatus status) {
        var snapshot = order(status);
        when(orders.findById("order-1")).thenReturn(Optional.of(snapshot));
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        var type = switch (status) {
            case APPROVED -> NotificationType.ORDER_APPROVED;
            case DENIED -> NotificationType.ORDER_DENIED;
            case SHIPPED_PART -> NotificationType.ORDER_SHIPPED;
            default -> NotificationType.ORDER_COMPLETED;
        };
        assertDoesNotThrow(() -> listener.receive(json(type)));
        assertEquals(status, snapshot.status());
        verify(orders).findById("order-1");
        verifyNoMoreInteractions(orders);
        verify(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectsMalformedAndInvalidNotifications() {
        for (String json : Arrays.asList(null, "{", "null", "{}", "[]",
                "{\"notificationId\":\"n\",\"orderId\":\"o\",\"notificationType\":\"UNKNOWN\"}"))
            assertDoesNotThrow(() -> listener.receive(json));
        verifyNoInteractions(orders, mail);
    }

    @Test
    void missingOrderAndUnknownShipmentReceiptDoNotSend() {
        listener.receive(json(NotificationType.ORDER_APPROVED));
        when(orders.findById("order-1")).thenReturn(Optional.of(order(OrderStatus.COMPLETED)));
        listener.receive(json(NotificationType.ORDER_SHIPPED).replace("pass-1", "unknown-pass"));
        verifyNoInteractions(mail);
    }

    @Test
    void eventSchemaAndSerializedMessageContainOnlyWorkflowIdentifiers() {
        assertEquals(Set.of("notificationId", "orderId", "notificationType", "shipmentEventId"),
                Arrays.stream(NotificationRequested.class.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet()));
        var jms = mock(JmsTemplate.class);
        new NotificationPublisher(jms, mapper, "petstore.notification.requested", true)
                .publish("order-1", NotificationType.ORDER_SHIPPED, "pass-1");
        var sent = ArgumentCaptor.forClass(String.class);
        verify(jms).convertAndSend(eq("petstore.notification.requested"), sent.capture());
        var tree = mapper.readTree(sent.getValue());
        assertEquals(4, tree.size());
        assertNotNull(java.util.UUID.fromString(tree.get("notificationId").asString()));
        assertEquals("order-1", tree.get("orderId").asString());
        assertEquals("ORDER_SHIPPED", tree.get("notificationType").asString());
        assertEquals("pass-1", tree.get("shipmentEventId").asString());
    }

    @Test
    void optionalPublisherDoesNothingWhenDisabled() {
        var jms = mock(JmsTemplate.class);
        new NotificationPublisher(jms, mapper, "queue", false).publish("order-1", NotificationType.ORDER_APPROVED, null);
        verifyNoInteractions(jms);
    }

    @Test
    void notificationPublicationFailureDoesNotEscape() {
        var jms = mock(JmsTemplate.class);
        doThrow(new UncategorizedJmsException("unavailable")).when(jms).convertAndSend(anyString(), any(String.class));
        var publisher = new NotificationPublisher(jms, mapper, "queue", true);
        assertDoesNotThrow(() -> publisher.publish("order-1", NotificationType.ORDER_DENIED, null));
    }

    private String json(NotificationType type) {
        return mapper.writeValueAsString(new NotificationRequested("notification-1", "order-1", type,
                type == NotificationType.ORDER_SHIPPED || type == NotificationType.ORDER_COMPLETED ? "pass-1" : null));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" "})
    void missingEmailSnapshotDoesNotSendOrWrite(String email) {
        var source = order(OrderStatus.APPROVED);
        var missing = new Order(source.id(), source.customerId(), source.username(), email, source.createdAt(),
                source.locale(), source.status(), source.billingInfo(), source.shippingInfo(), source.payment(),
                source.lineItems(), source.totalPrice(), source.fulfilmentEventIds(), source.version());
        when(orders.findById("order-1")).thenReturn(Optional.of(missing));
        assertDoesNotThrow(() -> listener.receive(json(NotificationType.ORDER_APPROVED)));
        verify(orders).findById("order-1");
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(mail);
    }
    @Test
    void malformedTypesAndScalarMessagesDoNotReachMailOrRepository() {
        for (String json : new String[] {null, "", "[]", "true", "1",
                "{\"notificationId\":\"n\",\"orderId\":\"o\",\"notificationType\":\"UNKNOWN\"}",
                "{\"notificationId\":\"n\",\"orderId\":\"o\",\"notificationType\":null}"})
            assertDoesNotThrow(() -> listener.receive(json));
        verifyNoInteractions(orders, mail);
    }

    private Order order(OrderStatus status) { return order(status, "en-US"); }

    private Order order(OrderStatus status, String locale) {
        var contact = new ContactSnapshot("Private", "Buyer", "billing@example.com", "555", "Private Street",
                null, "Dublin", null, "D01", "IE");
        return new Order("order-1", "customer", "user", "snapshot@example.com", Instant.EPOCH, locale,
                status, contact, contact, new PaymentSnapshot("VISA", "9876"), List.of(), BigDecimal.ONE,
                List.of("pass-1"), 1L);
    }
}
