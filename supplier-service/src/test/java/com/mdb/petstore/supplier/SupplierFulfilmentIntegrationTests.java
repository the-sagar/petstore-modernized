package com.mdb.petstore.supplier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import com.mdb.petstore.supplier.fulfilment.repository.SupplierOrderRepository;
import com.mdb.petstore.supplier.fulfilment.service.SupplierFulfilmentService;
import com.mdb.petstore.supplier.inventory.repository.InventoryRepository;
import com.mdb.petstore.supplier.inventory.seed.InventorySeeder;
import com.mdb.petstore.supplier.inventory.service.InventoryService;
import com.mdb.petstore.supplier.messaging.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jms.listener.auto-startup=false")
@AutoConfigureMockMvc
class SupplierFulfilmentIntegrationTests {
    private static final String DATABASE = "petstore_supplier_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired InventoryRepository inventory;
    @MockitoSpyBean SupplierOrderRepository orders;
    @MockitoBean InventoryFulfilledPublisher publisher;
    @Autowired InventorySeeder seeder;
    @Autowired InventoryService inventoryService;
    @Autowired SupplierFulfilmentService service;
    @Autowired InventoryRequestedListener listener;
    @Autowired ObjectMapper mapper;
    @Autowired MongoTemplate mongo;
    @Autowired MockMvc mvc;

    @BeforeEach
    void reset() throws Exception {
        assertEquals(DATABASE, mongo.getDb().getName());
        orders.deleteAll();
        inventory.deleteAll();
        seeder.run(null);
    }
    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName());
        mongo.getDb().drop();
    }
    @Test
    void seedsAllVerifiedIdsAndNeverResetsExistingStock() throws Exception {
        assertEquals(29, inventory.count());
        for (int i = 1; i <= 29; i++) assertEquals(10000, stock("EST-" + i));
        inventoryService.setQuantity("EST-1", 7);
        seeder.run(null);
        assertEquals(7, stock("EST-1"));
        inventory.deleteById("EST-29");
        seeder.run(null);
        assertEquals(10000, stock("EST-29"));
        assertEquals(7, stock("EST-1"));
    }
    @Test
    void sufficientStockCompletesAndDuplicateRequestDoesNotShipTwice() {
        request("one", line(1, "EST-1", 3));
        var completed = orders.findById("one").orElseThrow();
        assertEquals(SupplierOrder.Status.COMPLETED, completed.status());
        assertEquals(3, completed.lines().getFirst().quantityShipped());
        assertEquals(9997, stock("EST-1"));
        request("one", line(1, "EST-1", 3));
        assertEquals(1, orders.count());
        assertEquals(completed, orders.findById("one").orElseThrow());
        assertEquals(9997, stock("EST-1"));
        verify(publisher, times(1)).publish(eq("one"), any());
    }
    @Test
    void insufficientStockDoesNotPartiallyShipAnIndividualLine() {
        inventoryService.setQuantity("EST-1", 2);
        request("short", line(1, "EST-1", 3));
        assertEquals(2, stock("EST-1"));
        assertEquals(0, orders.findById("short").orElseThrow().lines().getFirst().quantityShipped());
        assertEquals(SupplierOrder.Status.PENDING, orders.findById("short").orElseThrow().status());
        verifyNoInteractions(publisher);
    }
    @Test
    void unknownInventoryIsUnavailableWithoutInventingStock() {
        request("unknown", line(1, "UNKNOWN", 1));
        assertFalse(inventory.existsById("UNKNOWN"));
        assertEquals(SupplierOrder.Status.PENDING, orders.findById("unknown").orElseThrow().status());
        verifyNoInteractions(publisher);
    }
    @Test
    void shipsAvailableLinesEvenAfterUnavailableLine() {
        inventoryService.setQuantity("EST-1", 0);
        request("partial", line(1, "EST-1", 2), line(2, "EST-2", 5));
        var order = orders.findById("partial").orElseThrow();
        assertEquals(SupplierOrder.Status.PENDING, order.status());
        assertEquals(0, order.lines().get(0).quantityShipped());
        assertEquals(5, order.lines().get(1).quantityShipped());
        assertEquals(9995, stock("EST-2"));
        var pass = order.shipments().getFirst();
        assertFalse(pass.complete());
        assertEquals(List.of(new SupplierOrder.ShippedLine(2, "EST-2", 5)), pass.shippedLines());
        verify(publisher).publish(eq("partial"), argThat(sent -> sent.eventId().equals(pass.eventId())
                && sent.shippedLines().equals(pass.shippedLines()) && !sent.complete()));
    }
    @Test
    void replenishmentCompletesPendingOrderAutomatically() throws Exception {
        inventoryService.setQuantity("EST-1", 0);
        request("waiting", line(1, "EST-1", 3));
        mvc.perform(put("/api/inventory/EST-1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(2));
        assertEquals(2, stock("EST-1"));
        var order = orders.findById("waiting").orElseThrow();
        assertEquals(SupplierOrder.Status.COMPLETED, order.status());
        assertTrue(order.shipments().getFirst().complete());
        verify(publisher).publish(eq("waiting"), argThat(sent -> sent.eventId().equals(order.shipments().getFirst().eventId())
                && sent.complete()));
    }
    @Test
    void partialThenCompletePublishesOnlyNewLinesPerPass() {
        inventoryService.setQuantity("EST-2", 0);
        request("two-pass", line(1, "EST-1", 2), line(2, "EST-2", 5));
        inventoryService.setQuantity("EST-2", 8);
        var order = orders.findById("two-pass").orElseThrow();
        assertEquals(SupplierOrder.Status.COMPLETED, order.status());
        assertEquals(9998, stock("EST-1"));
        assertEquals(3, stock("EST-2"));
        var capture = ArgumentCaptor.forClass(SupplierOrder.Shipment.class);
        verify(publisher, times(2)).publish(eq("two-pass"), capture.capture());
        var passes = capture.getAllValues();
        assertEquals(List.of(new SupplierOrder.ShippedLine(1, "EST-1", 2)), passes.get(0).shippedLines());
        assertEquals(List.of(new SupplierOrder.ShippedLine(2, "EST-2", 5)), passes.get(1).shippedLines());
        assertFalse(passes.get(0).complete());
        assertTrue(passes.get(1).complete());
        assertNotEquals(passes.get(0).eventId(), passes.get(1).eventId());
    }
    @Test
    void concurrentOrdersCannotOversell() throws Exception {
        inventoryService.setQuantity("EST-1", 5);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 6; i++) {
                String id = "concurrent-" + i;
                futures.add(executor.submit(() -> { start.await(); request(id, line(1, "EST-1", 3)); return null; }));
            }
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        assertEquals(2, stock("EST-1"));
        assertEquals(3, orders.findAll().stream().flatMap(o -> o.lines().stream()).mapToInt(SupplierOrder.Line::quantityShipped).sum());
    }
    @Test
    void concurrentDuplicateRequestsCreateAndShipOnlyOneOrder() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> request("duplicate", line(1, "EST-1", 2)));
            var b = executor.submit(() -> request("duplicate", line(1, "EST-1", 2)));
            a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, orders.count());
        assertEquals(9998, stock("EST-1"));
        verify(publisher, times(1)).publish(eq("duplicate"), any());
    }
    @Test
    void saveFailureRollsBackAllStockDeductions() {
        doThrow(new IllegalStateException("Simulated write failure")).when(orders).save(any(SupplierOrder.class));
        assertThrows(IllegalStateException.class, () -> request("rollback", line(1, "EST-1", 2), line(2, "EST-2", 4)));
        assertEquals(10000, stock("EST-1"));
        assertEquals(10000, stock("EST-2"));
        assertEquals(0, orders.findById("rollback").orElseThrow().lines().getFirst().quantityShipped());
        verifyNoInteractions(publisher);
    }
    @Test
    void publicationFailurePreservesCommittedStateAndHistoryWithoutDoubleDeduction() {
        doThrow(new UncategorizedJmsException("Broker unavailable")).when(publisher).publish(anyString(), any());
        assertThrows(UncategorizedJmsException.class, () -> request("send-failure", line(1, "EST-1", 2)));
        assertEquals(9998, stock("EST-1"));
        var order = orders.findById("send-failure").orElseThrow();
        assertEquals(SupplierOrder.Status.COMPLETED, order.status());
        assertEquals(1, order.shipments().size());
        request("send-failure", line(1, "EST-1", 2));
        assertEquals(9998, stock("EST-1"));
        verify(publisher, times(1)).publish(anyString(), any());
    }
    @Test
    void conflictingDuplicateCannotAlterOriginalSupplierOrder() {
        request("conflict", line(1, "EST-1", 2));
        request("conflict", line(1, "EST-1", 8));
        assertEquals(9998, stock("EST-1"));
        assertEquals(2, orders.findById("conflict").orElseThrow().lines().getFirst().quantityRequested());
    }
    @Test
    void rejectsMalformedAndInvalidRequestsWithoutMutation() {
        for (String json : List.of("{", "null", "{}", "{\"orderId\":\"x\",\"lines\":[]}")) listener.receive(json);
        listener.receive(mapper.writeValueAsString(new InventoryRequested(" ", List.of(line(1, "EST-1", 1)))));
        listener.receive(mapper.writeValueAsString(new InventoryRequested("x", List.of(line(1, "EST-1", 0)))));
        listener.receive(mapper.writeValueAsString(new InventoryRequested("x", List.of(line(1, "EST-1", 1), line(1, "EST-2", 1)))));
        assertEquals(0, orders.count());
        assertEquals(10000, stock("EST-1"));
    }
    @Test
    void inventoryApiListsGetsAndSetsExactNonnegativeQuantities() throws Exception {
        mvc.perform(get("/api/inventory")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(29));
        mvc.perform(get("/api/inventory/EST-1")).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(10000));
        mvc.perform(put("/api/inventory/EST-1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(25));
        mvc.perform(put("/api/inventory/EST-1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
        for (String bad : List.of("{\"quantity\":-1}", "{\"quantity\":null}", "{}"))
            mvc.perform(put("/api/inventory/EST-1").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/UNKNOWN")).andExpect(status().isNotFound());
        mvc.perform(put("/api/inventory/UNKNOWN").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/inventory/retry-pending")).andExpect(status().isOk());
    }
    @Test
    void exactStockAcrossRepeatedItemLinesAndRepeatedRetriesNeverOversells() {
        inventoryService.setQuantity("EST-1", 3);
        request("same-item", line(1, "EST-1", 2), line(2, "EST-1", 2));
        assertEquals(1, stock("EST-1"));
        assertEquals(1, orders.findById("same-item").orElseThrow().shipments().size());
        service.retryPending();
        assertEquals(1, stock("EST-1"));
        inventoryService.setQuantity("EST-1", 2);
        assertEquals(0, stock("EST-1"));
        assertEquals(SupplierOrder.Status.COMPLETED, orders.findById("same-item").orElseThrow().status());
        service.retryPending();
        request("same-item", line(1, "EST-1", 2), line(2, "EST-1", 2));
        assertEquals(2, orders.findById("same-item").orElseThrow().shipments().size());
        verify(publisher, times(2)).publish(eq("same-item"), any());
    }
    @Test
    void nullScalarAndNestedInvalidRequestsCreateNoSupplierOrder() {
        for (String json : new String[] {null, "", "null", "[]", "true", "1",
                "{\"orderId\":\"bad\",\"lines\":[null]}",
                "{\"orderId\":\"bad\",\"lines\":[{\"lineNumber\":1,\"itemId\":\"EST-1\",\"quantity\":2147483648}]}"})
            assertDoesNotThrow(() -> listener.receive(json));
        assertEquals(0, orders.count());
        assertEquals(10000, stock("EST-1"));
        verifyNoInteractions(publisher);
    }
    @Test
    void inventoryIntegerBoundaryIsExactAndOverflowRejected() throws Exception {
        mvc.perform(put("/api/inventory/EST-1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2147483647}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(Integer.MAX_VALUE));
        mvc.perform(put("/api/inventory/EST-1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2147483648}"))
                .andExpect(status().isBadRequest());
        assertEquals(Integer.MAX_VALUE, stock("EST-1"));
    }

    @Test
    void optimisticConflictRollsBackBeforeRetryingAllocation() {
        doThrow(new org.springframework.dao.OptimisticLockingFailureException("Simulated competing save"))
                .doAnswer(mockingDetails(orders).getMockCreationSettings().getDefaultAnswer())
                .when(orders).save(any(SupplierOrder.class));
        request("retry-conflict", line(1, "EST-1", 2));
        assertEquals(9998, stock("EST-1"));
        var order = orders.findById("retry-conflict").orElseThrow();
        assertEquals(SupplierOrder.Status.COMPLETED, order.status());
        assertEquals(1, order.shipments().size());
        verify(publisher).publish(eq("retry-conflict"), any());
    }

    private int stock(String id) { return inventory.findById(id).orElseThrow().quantity(); }
    private InventoryRequested.Line line(int number, String id, int quantity) { return new InventoryRequested.Line(number, id, quantity); }
    private void request(String id, InventoryRequested.Line... lines) {
        listener.receive(mapper.writeValueAsString(new InventoryRequested(id, List.of(lines))));
    }
}
