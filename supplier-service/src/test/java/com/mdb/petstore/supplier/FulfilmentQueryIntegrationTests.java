package com.mdb.petstore.supplier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import com.mdb.petstore.supplier.fulfilment.repository.SupplierOrderRepository;
import com.mdb.petstore.supplier.messaging.InventoryFulfilledPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jms.listener.auto-startup=false")
@AutoConfigureMockMvc
class FulfilmentQueryIntegrationTests {
    private static final String DATABASE = "petstore_supplier_query_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired SupplierOrderRepository orders;
    @Autowired MongoTemplate mongo;
    @Autowired MockMvc mvc;
    @MockitoBean InventoryFulfilledPublisher publisher;
    @BeforeEach void reset() { assertEquals(DATABASE, mongo.getDb().getName()); orders.deleteAll(); }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) { assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop(); }
    @Test void listsAllNewestFirstWithIdTieBreak() throws Exception {
        save("old", SupplierOrder.Status.PENDING, NOW.minusSeconds(1));
        save("b", SupplierOrder.Status.COMPLETED, NOW); save("a", SupplierOrder.Status.PENDING, NOW);
        mvc.perform(get("/api/fulfilment/orders")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", contains("a", "b", "old")));
    }
    @Test void filtersPendingAndCompleted() throws Exception {
        save("pending", SupplierOrder.Status.PENDING, NOW); save("done", SupplierOrder.Status.COMPLETED, NOW);
        mvc.perform(get("/api/fulfilment/orders").param("status", "PENDING")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", contains("pending")));
        mvc.perform(get("/api/fulfilment/orders").param("status", "COMPLETED")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", contains("done")));
    }
    @Test void detailShowsProgressAndShipmentHistoryWithoutPersistenceInternals() throws Exception {
        save("partial", SupplierOrder.Status.PENDING, NOW);
        mvc.perform(get("/api/fulfilment/orders/partial")).andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedLineCount").value(2)).andExpect(jsonPath("$.shippedLineCount").value(1))
                .andExpect(jsonPath("$.lines[0].quantityRequested").value(2)).andExpect(jsonPath("$.lines[0].quantityShipped").value(2))
                .andExpect(jsonPath("$.lines[0].remainingQuantity").value(0)).andExpect(jsonPath("$.lines[1].remainingQuantity").value(1))
                .andExpect(jsonPath("$.shipments[0].eventId").value("pass-1"))
                .andExpect(jsonPath("$.shipments[0].complete").value(false))
                .andExpect(jsonPath("$.shipments[0].shippedLines[0].quantity").value(2))
                .andExpect(jsonPath("$.version").doesNotExist()).andExpect(jsonPath("$._id").doesNotExist())
                .andExpect(jsonPath("$._class").doesNotExist()).andExpect(jsonPath("$.payment").doesNotExist());
        mvc.perform(get("/api/fulfilment/orders")).andExpect(content().string(not(containsString("version"))));
    }
    @Test void unknownOrderReturns404() throws Exception {
        mvc.perform(get("/api/fulfilment/orders/missing")).andExpect(status().isNotFound());
    }
    @Test void invalidFilterIs400AndEmptyListIsValid() throws Exception {
        mvc.perform(get("/api/fulfilment/orders").param("status", "APPROVED")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/fulfilment/orders")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
    private void save(String id, SupplierOrder.Status status, Instant created) {
        boolean complete = status == SupplierOrder.Status.COMPLETED;
        orders.insert(new SupplierOrder(id, status,
                List.of(new SupplierOrder.Line(1, "EST-1", 2, 2), new SupplierOrder.Line(2, "EST-15", 1, complete ? 1 : 0)),
                List.of(new SupplierOrder.Shipment("pass-1", List.of(new SupplierOrder.ShippedLine(1, "EST-1", 2)), false, created)),
                created, created, null));
    }
}
