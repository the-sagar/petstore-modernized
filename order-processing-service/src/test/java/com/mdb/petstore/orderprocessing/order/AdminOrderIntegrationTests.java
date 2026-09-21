package com.mdb.petstore.orderprocessing.order;

import com.mdb.petstore.orderprocessing.order.notification.NotificationPublisher;
import com.mdb.petstore.orderprocessing.order.notification.NotificationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.mdb.petstore.orderprocessing.order.admin.service.AdminOrderService;
import com.mdb.petstore.orderprocessing.order.messaging.InventoryRequestedPublisher;
import com.mdb.petstore.orderprocessing.order.messaging.OrderSubmittedListener;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderLine;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.model.ContactSnapshot;
import com.mdb.petstore.orderprocessing.order.model.PaymentSnapshot;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import com.mdb.petstore.orderprocessing.order.service.OrderApprovalService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jms.listener.auto-startup=false")
@AutoConfigureMockMvc
class AdminOrderIntegrationTests {
    private static final String DATABASE = "petstore_admin_orders_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired MongoTemplate mongo;
    @Autowired AdminOrderService service;
    @Autowired OrderSubmittedListener submitted;
    @MockitoSpyBean OrderApprovalService approval;
    @MockitoBean InventoryRequestedPublisher publisher;
    @MockitoBean NotificationPublisher notifications;
    @BeforeEach
    void reset() { assertEquals(DATABASE, mongo.getDb().getName()); orders.deleteAll(); }
    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }
    @Test
    void filtersEveryStatusAndSortsNewestThenId() throws Exception {
        for (var status : OrderStatus.values()) save(status.name(), status, Instant.parse("2026-01-01T00:00:00Z"));
        save("pending-b", OrderStatus.PENDING, Instant.parse("2026-02-01T00:00:00Z"));
        save("pending-a", OrderStatus.PENDING, Instant.parse("2026-02-01T00:00:00Z"));
        mvc.perform(get("/api/admin/orders").param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].orderId").value("pending-a"))
                .andExpect(jsonPath("$[1].orderId").value("pending-b"))
                .andExpect(jsonPath("$[2].orderId").value("PENDING"));
        for (var state : List.of(OrderStatus.APPROVED, OrderStatus.DENIED, OrderStatus.SHIPPED_PART, OrderStatus.COMPLETED))
            mvc.perform(get("/api/admin/orders").param("status", state.name())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value(state.name()));
        mvc.perform(get("/api/admin/orders")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7)).andExpect(jsonPath("$[0].orderId").value("pending-a"));
        mvc.perform(get("/api/admin/orders").param("status", "INVALID")).andExpect(status().isBadRequest());
    }
    @Test
    void returnsSafeListAndDetail() throws Exception {
        save("safe", OrderStatus.PENDING, Instant.now());
        for (String path : List.of("/api/admin/orders", "/api/admin/orders/safe")) {
            var json = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            for (String secret : List.of("payment", "last4", "billingInfo", "shippingInfo", "version", "fulfilmentEventIds", "private@example.com", "Private Street"))
                assertFalse(json.contains(secret), secret);
        }
        mvc.perform(get("/api/admin/orders/safe")).andExpect(jsonPath("$.orderId").value("safe"))
                .andExpect(jsonPath("$.username").value("customer"))
                .andExpect(jsonPath("$.lines[0].quantityShipped").value(0))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(10));
    }
    @Test
    void unknownOrderIs404ForEveryOperation() throws Exception {
        mvc.perform(get("/api/admin/orders/missing")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/orders/missing/approve")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/orders/missing/deny")).andExpect(status().isNotFound());
        verifyNoInteractions(publisher, notifications);
    }
    @Test
    void approveReusesBoundaryPublishesOnceAndDuplicateConflicts() throws Exception {
        save("approve", OrderStatus.PENDING, Instant.now());
        mvc.perform(post("/api/admin/orders/approve/approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/admin/orders/approve/approve")).andExpect(status().isConflict());
        verify(approval, times(1)).approve(any(Order.class));
        verify(publisher, times(1)).publish(argThat(o -> o.id().equals("approve")));
        verify(notifications).publish("approve", NotificationType.ORDER_APPROVED, null);
        verifyNoMoreInteractions(notifications);
    }
    @Test
    void nonPendingDecisionsAlwaysConflict() throws Exception {
        for (var state : List.of(OrderStatus.APPROVED, OrderStatus.DENIED, OrderStatus.SHIPPED_PART, OrderStatus.COMPLETED)) {
            save(state.name(), state, Instant.now());
            mvc.perform(post("/api/admin/orders/" + state + "/approve")).andExpect(status().isConflict());
            mvc.perform(post("/api/admin/orders/" + state + "/deny")).andExpect(status().isConflict());
            assertEquals(state, orders.findById(state.name()).orElseThrow().status());
        }
        verifyNoInteractions(publisher, notifications);
    }
    @Test
    void denyNotifiesOnceAndSurvivesDuplicateSubmittedEvent() throws Exception {
        save("denied", OrderStatus.PENDING, Instant.now()); // Total 10 would otherwise auto-approve.
        mvc.perform(post("/api/admin/orders/denied/deny")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"));
        submitted.receive("{\"orderId\":\"denied\"}");
        mvc.perform(post("/api/admin/orders/denied/deny")).andExpect(status().isConflict());
        assertEquals(OrderStatus.DENIED, orders.findById("denied").orElseThrow().status());
        verify(notifications).publish("denied", NotificationType.ORDER_DENIED, null);
        verifyNoMoreInteractions(notifications);
        verifyNoInteractions(publisher);
    }
    @Test
    void concurrentApprovalsPublishInventoryOnlyOnce() throws Exception {
        save("race", OrderStatus.PENDING, Instant.now());
        var statuses = race(true, true);
        assertEquals(List.of(200, 409), statuses);
        assertEquals(OrderStatus.APPROVED, orders.findById("race").orElseThrow().status());
        verify(publisher, times(1)).publish(any());
    }
    @Test
    void concurrentApproveAndDenyHaveExactlyOneWinner() throws Exception {
        save("race", OrderStatus.PENDING, Instant.now());
        assertEquals(List.of(200, 409), race(true, false));
        var status = orders.findById("race").orElseThrow().status();
        assertTrue(status == OrderStatus.APPROVED || status == OrderStatus.DENIED);
        verify(publisher, times(status == OrderStatus.APPROVED ? 1 : 0)).publish(any());
        verify(notifications).publish("race", status == OrderStatus.APPROVED
                ? NotificationType.ORDER_APPROVED : NotificationType.ORDER_DENIED, null);
        verifyNoMoreInteractions(notifications);
    }
    @Test
    void failedPublicationDoesNotRollBackApproval() {
        save("failed-send", OrderStatus.PENDING, Instant.now());
        doThrow(new UncategorizedJmsException("Unavailable")).when(publisher).publish(any());
        assertThrows(UncategorizedJmsException.class, () -> service.approve("failed-send"));
        assertEquals(OrderStatus.APPROVED, orders.findById("failed-send").orElseThrow().status());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.approve("failed-send")).getStatusCode().value());
        verify(publisher, times(1)).publish(any());
    }
    @Test
    void concurrentDenialsNotifyExactlyOneWinningTransition() throws Exception {
        save("race", OrderStatus.PENDING, Instant.now());
        assertEquals(List.of(200, 409), race(false, false));
        assertEquals(OrderStatus.DENIED, orders.findById("race").orElseThrow().status());
        verify(notifications).publish("race", NotificationType.ORDER_DENIED, null);
        verifyNoMoreInteractions(notifications);
        verifyNoInteractions(publisher);
    }

    private List<Integer> race(boolean a, boolean b) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return decide(a); });
            var second = executor.submit(() -> { start.await(); return decide(b); });
            start.countDown();
            return java.util.stream.Stream.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).sorted().toList();
        }
    }
    private int decide(boolean approve) throws Exception {
        return mvc.perform(post("/api/admin/orders/race/" + (approve ? "approve" : "deny")))
                .andReturn().getResponse().getStatus();
    }
    private void save(String id, OrderStatus status, Instant time) {
        var contact = new ContactSnapshot("Private", "Contact", "private@example.com", "555", "Private Street", null, "Dublin", null, "D01", "IE");
        orders.insert(new Order(id, "customer-id", "customer", "private@example.com", time, "en-US", status,
                contact, contact, new PaymentSnapshot("VISA", "1111"),
                List.of(new OrderLine(1, "FISH", "FI-SW-01", "EST-1", 1, new BigDecimal("10.00"))), new BigDecimal("10.00")));
    }
}
