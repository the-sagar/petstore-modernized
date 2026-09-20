package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.mdb.petstore.orderprocessing.order.messaging.*;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderLine;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import com.mdb.petstore.orderprocessing.order.service.OrderApprovalService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.jms.listener.auto-startup=false", "petstore.notification.enabled=true"})
class InventoryFulfilmentIntegrationTests {
    private static final String DATABASE = "petstore_fulfilment_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired OrderRepository orders;
    @Autowired MongoTemplate mongo;
    @Autowired InventoryFulfilledListener listener;
    @Autowired OrderApprovalService approval;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean JmsTemplate jms;
    @BeforeEach
    void reset() {
        assertEquals(DATABASE, mongo.getDb().getName());
        orders.deleteAll();
        doNothing().when(jms).convertAndSend(anyString(), any(String.class));
    }
    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }
    @Test
    void approvalPublishesOnlyFulfilmentIdentityAfterStatusPersists() {
        var order = order(OrderStatus.PENDING);
        doAnswer(call -> {
            assertEquals(OrderStatus.APPROVED, reload().status());
            var json = mapper.readTree((String) call.getArgument(1));
            assertEquals(2, json.size());
            assertEquals("order-1", json.get("orderId").asString());
            assertEquals(3, json.get("lines").get(0).size());
            assertEquals(2, json.get("lines").get(0).get("quantity").asInt());
            return null;
        }).when(jms).convertAndSend(eq("petstore.inventory.requested"), any(String.class));
        assertTrue(approval.approve(order));
        assertFalse(approval.approve(order));
        verify(jms, times(1)).convertAndSend(eq("petstore.inventory.requested"), any(String.class));
        verify(jms, times(1)).convertAndSend(eq("petstore.notification.requested"), any(String.class));
    }
    @Test
    void failedInventoryRequestPublicationLeavesApprovedForReconciliation() {
        var order = order(OrderStatus.PENDING);
        doThrow(new UncategorizedJmsException("unavailable")).when(jms).convertAndSend(anyString(), any(String.class));
        assertThrows(UncategorizedJmsException.class, () -> approval.approve(order));
        assertEquals(OrderStatus.APPROVED, reload().status());
        assertFalse(approval.approve(order));
        verify(jms, times(1)).convertAndSend(eq("petstore.inventory.requested"), any(String.class));
        verify(jms, times(1)).convertAndSend(eq("petstore.notification.requested"), any(String.class));
    }
    @Test
    void partialThenFinalTracksQuantitiesAndPreservesSnapshot() {
        var before = order(OrderStatus.APPROVED);
        deliver("pass-1", false, line(1, "EST-1", 2));
        assertEquals(OrderStatus.SHIPPED_PART, reload().status());
        assertEquals(2, reload().lineItems().getFirst().quantityShipped());
        assertEquals(0, reload().lineItems().get(1).quantityShipped());
        assertNotifications("ORDER_SHIPPED:pass-1");
        deliver("pass-2", true, line(2, "EST-2", 5));
        assertEquals(OrderStatus.COMPLETED, reload().status());
        assertEquals(before.totalPrice(), reload().totalPrice());
        assertEquals(before.createdAt(), reload().createdAt());
        assertEquals(before.lineItems().getFirst().unitPrice(), reload().lineItems().getFirst().unitPrice());
        assertEquals(List.of("pass-1", "pass-2"), reload().fulfilmentEventIds());
        assertNotifications("ORDER_SHIPPED:pass-1", "ORDER_SHIPPED:pass-2", "ORDER_COMPLETED:pass-2");
    }
    @Test
    void duplicatePassDoesNotIncrementTwiceEvenBeforeCompletion() {
        order(OrderStatus.APPROVED);
        deliver("pass-1", false, line(1, "EST-1", 1));
        var partial = reload();
        deliver("pass-1", false, line(1, "EST-1", 1));
        assertEquals(partial, reload());
        assertEquals(1, reload().lineItems().getFirst().quantityShipped());
        assertNotifications("ORDER_SHIPPED:pass-1");
    }
    @Test
    void completedOrderStableOnDuplicateAndDifferentEventId() {
        order(OrderStatus.APPROVED);
        deliver("complete", true, line(1, "EST-1", 2), line(2, "EST-2", 5));
        var completed = reload();
        assertEquals(OrderStatus.COMPLETED, completed.status());
        deliver("complete", true, line(1, "EST-1", 2), line(2, "EST-2", 5));
        deliver("another", true, line(1, "EST-1", 2));
        assertEquals(completed, reload());
        assertNotifications("ORDER_SHIPPED:complete", "ORDER_COMPLETED:complete");
    }
    @Test
    void doesNotTrustCompleteHintOrDeliveryOrder() {
        order(OrderStatus.APPROVED);
        deliver("later-pass", true, line(2, "EST-2", 5));
        assertEquals(OrderStatus.SHIPPED_PART, reload().status());
        deliver("earlier-pass", false, line(1, "EST-1", 2));
        assertEquals(OrderStatus.COMPLETED, reload().status());
    }
    @Test
    void ignoresInvalidLineItemOverfulfilmentAndDuplicateLinesAtomically() {
        var before = order(OrderStatus.APPROVED);
        for (var lines : List.of(List.of(line(3, "EST-1", 2)), List.of(line(1, "WRONG", 2)),
                List.of(line(1, "EST-1", 3)), List.of(line(1, "EST-1", 2), line(2, "EST-2", 6)),
                List.of(line(1, "EST-1", 1), line(1, "EST-1", 1)))) {
            deliver("invalid", true, lines.toArray(InventoryFulfilled.Line[]::new));
            assertEquals(before, reload());
        }
    }
    @Test
    void pendingAndDeniedOrdersCannotBeCompleted() {
        for (var status : List.of(OrderStatus.PENDING, OrderStatus.DENIED)) {
            orders.deleteAll(); var before = order(status);
            deliver("invalid", true, line(1, "EST-1", 2), line(2, "EST-2", 5));
            assertEquals(before, reload());
        }
    }
    @Test
    void malformedEventsAndUnknownOrdersDoNotCreateOrders() {
        for (String json : List.of("{", "null", "{}")) listener.receive(json);
        deliver("missing", true, line(1, "EST-1", 2));
        assertEquals(0, orders.count());
    }
    @Test
    void concurrentDifferentAndDuplicatePassesAreSafe() throws Exception {
        order(OrderStatus.APPROVED);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> deliver("a", false, line(1, "EST-1", 2)));
            var duplicate = executor.submit(() -> deliver("a", false, line(1, "EST-1", 2)));
            var b = executor.submit(() -> deliver("b", true, line(2, "EST-2", 5)));
            a.get(); duplicate.get(); b.get();
        }
        assertEquals(OrderStatus.COMPLETED, reload().status());
        assertEquals(2, reload().fulfilmentEventIds().size());
        assertEquals(2, reload().lineItems().getFirst().quantityShipped());
        assertEquals(5, reload().lineItems().get(1).quantityShipped());
        verify(jms, times(3)).convertAndSend(eq("petstore.notification.requested"), any(String.class));
    }
    @Test
    void preexistingOrderWithoutTrackingFieldsCanBeFulfilled() {
        order(OrderStatus.APPROVED);
        mongo.getCollection("orders").updateOne(new org.bson.Document("_id", "order-1"),
                new org.bson.Document("$unset", new org.bson.Document("version", "").append("fulfilmentEventIds", "")
                        .append("lineItems.0.quantityShipped", "").append("lineItems.1.quantityShipped", "")));
        assertEquals(0, reload().lineItems().getFirst().quantityShipped());
        deliver("legacy-order", true, line(1, "EST-1", 2), line(2, "EST-2", 5));
        assertEquals(OrderStatus.COMPLETED, reload().status());
    }
    @Test
    void notificationBrokerFailureDoesNotFailAppliedFulfilment() {
        order(OrderStatus.APPROVED);
        doThrow(new UncategorizedJmsException("unavailable")).when(jms)
                .convertAndSend(eq("petstore.notification.requested"), any(String.class));
        assertDoesNotThrow(() -> deliver("complete", true, line(1, "EST-1", 2), line(2, "EST-2", 5)));
        assertEquals(OrderStatus.COMPLETED, reload().status());
        assertEquals(List.of("complete"), reload().fulfilmentEventIds());
    }

    private void assertNotifications(String... expected) {
        var messages = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jms, times(expected.length)).convertAndSend(eq("petstore.notification.requested"), messages.capture());
        assertEquals(List.of(expected), messages.getAllValues().stream().map(json -> {
            var tree = mapper.readTree(json);
            assertEquals("order-1", tree.get("orderId").asString());
            return tree.get("notificationType").asString() + ":" + tree.get("shipmentEventId").asString();
        }).toList());
    }

    private Order order(OrderStatus status) {
        orders.insert(new Order("order-1", "customer", "user", "demo@example.com", Instant.now(), "en-US", status,
                null, null, null, List.of(new OrderLine(1, "FISH", "product-1", "EST-1", 2, new BigDecimal("10.00")),
                new OrderLine(2, "FISH", "product-2", "EST-2", 5, new BigDecimal("3.00"))), new BigDecimal("35.00")));
        return reload();
    }
    private Order reload() { return orders.findById("order-1").orElseThrow(); }
    private InventoryFulfilled.Line line(int number, String id, int quantity) { return new InventoryFulfilled.Line(number, id, quantity); }
    private void deliver(String eventId, boolean complete, InventoryFulfilled.Line... lines) {
        listener.receive(mapper.writeValueAsString(new InventoryFulfilled(eventId, "order-1", List.of(lines), complete)));
    }
}
