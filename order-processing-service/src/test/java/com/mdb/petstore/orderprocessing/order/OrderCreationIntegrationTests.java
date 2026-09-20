package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import com.mdb.petstore.orderprocessing.order.dto.OrderResponse;
import com.mdb.petstore.orderprocessing.order.messaging.OrderSubmitted;
import com.mdb.petstore.orderprocessing.order.messaging.OrderSubmittedListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jms.listener.auto-startup=false", "petstore.notification.enabled=true"})
@AutoConfigureMockMvc
class OrderCreationIntegrationTests {

    private static final String DATABASE = "petstore_orders_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String REQUEST = """
            {
              "customerId":"customer-test", "username":"order-user", "email":"account@example.com", "locale":"en-US",
              "billingInfo":{"firstName":"Bill","lastName":"Buyer","email":"billing@example.com","phone":"555-0100",
                "street1":"1 Billing St","street2":"Unit 2","city":"Dublin","stateOrProvince":"Dublin","postalCode":"D01","country":"IE"},
              "shippingInfo":{"firstName":"Ship","lastName":"Recipient","email":"shipping@example.com","phone":"555-0200",
                "street1":"2 Shipping St","city":"Cork","postalCode":"C01","country":"IE"},
              "payment":{"cardType":"VISA","last4":"1111"},
              "lineItems":[
                {"lineNumber":1,"categoryId":"FISH","productId":"FI-SW-01","itemId":"EST-1","quantity":3,"unitPrice":0.10},
                {"lineNumber":2,"categoryId":"CATS","productId":"FL-DSH-01","itemId":"EST-15","quantity":2,"unitPrice":23.50}
              ]
            }
            """;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }

    @MockitoSpyBean
    private JmsTemplate jms;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderRepository orders;
    @Autowired private MongoTemplate mongo;
    @Autowired private OrderSubmittedListener listener;


    @BeforeEach
    void resetOrders() {
        // Retain real converter configuration while replacing only the broker send.
        doNothing().when(jms).convertAndSend(anyString(), any(String.class));
        assertEquals(DATABASE, mongo.getDb().getName());
        orders.deleteAll();
    }

    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName());
        mongo.getDb().drop();
    }

    @Test
    void createsPendingOrderWithServerIdentityTimeAndCalculatedDecimalTotal() throws Exception {
        Instant before = Instant.now();
        var result = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andReturn();
        var response = mapper.readValue(result.getResponse().getContentAsByteArray(), OrderResponse.class);
        assertNotNull(UUID.fromString(response.orderId()));
        assertEquals("/api/orders/" + response.orderId(), result.getResponse().getHeader("Location"));
        assertEquals(OrderStatus.PENDING, response.status());
        assertFalse(response.createdAt().isBefore(before));
        assertFalse(response.createdAt().isAfter(Instant.now()));
        assertEquals(new BigDecimal("47.30"), response.totalPrice());
        assertEquals(1, orders.count());
        var order = orders.findById(response.orderId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.status());
        assertEquals(response.createdAt().truncatedTo(ChronoUnit.MILLIS), order.createdAt());
        assertEquals(new BigDecimal("47.30"), order.totalPrice());
        assertEquals(new BigDecimal("0.10"), order.lineItems().getFirst().unitPrice());
        assertEquals(3, order.lineItems().getFirst().quantity());
        assertEquals("customer-test", order.customerId());
        assertEquals("order-user", order.username());
        assertEquals("account@example.com", order.email());
        assertEquals("en-US", order.locale());
        assertEquals("1 Billing St", order.billingInfo().street1());
        assertEquals("2 Shipping St", order.shippingInfo().street1());
        assertThrows(UnsupportedOperationException.class, () -> order.lineItems().clear());
    }

    @Test
    void storesOnlyPaymentDisplayFieldsAndDecimal128Amounts() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated());
        Document document = mongo.getCollection("orders").find().first();
        assertNotNull(document);
        Document payment = document.get("payment", Document.class);
        assertEquals(Set.of("cardType", "last4"), payment.keySet());
        assertEquals("VISA", payment.getString("cardType"));
        assertEquals("1111", payment.getString("last4"));
        assertFalse(document.toJson().contains("cardNumber"));
        assertFalse(document.toJson().contains("4111111111111111"));
        assertInstanceOf(Decimal128.class, document.get("totalPrice"));
        assertInstanceOf(Decimal128.class, document.getList("lineItems", Document.class).getFirst().get("unitPrice"));
    }

    @Test
    void rejectsEmptyAndNullLineLists() throws Exception {
        for (String replacement : new String[] {"[]", "null", "[null]"}) {
            rejected(REQUEST.replaceFirst("(?s)\"lineItems\":\\[.*?\\]", "\"lineItems\":" + replacement));
        }
    }

    @Test
    void rejectsZeroAndNegativeQuantity() throws Exception {
        for (int quantity : new int[] {0, -1}) {
            rejected(REQUEST.replace("\"quantity\":3", "\"quantity\":" + quantity));
        }
    }

    @Test
    void rejectsNegativeMissingInvalidAndUnrepresentablePrices() throws Exception {
        for (String price : new String[] {"-0.01", "null", "\"invalid\"", "1E+7000"}) {
            rejected(REQUEST.replace("\"unitPrice\":0.10", "\"unitPrice\":" + price));
        }
    }

    @Test
    void allowsZeroPrice() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"unitPrice\":0.10", "\"unitPrice\":0")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalPrice").value(47));
    }

    @Test
    void rejectsBlankIdsAndInvalidCustomerOrContactData() throws Exception {
        for (String value : new String[] {"customer-test", "order-user", "FISH", "FI-SW-01", "EST-1", "Bill", "D01"}) {
            rejected(REQUEST.replace("\"" + value + "\"", "\" \""));
        }
        rejected(REQUEST.replace("account@example.com", "invalid"));
        rejected(REQUEST.replace("billing@example.com", "invalid"));
    }

    @Test
    void rejectsInvalidPaymentDisplayAndRawCredentials() throws Exception {
        rejected(REQUEST.replace("\"last4\":\"1111\"", "\"last4\":\"4111111111111111\""));
        rejected(REQUEST.replace("\"last4\":\"1111\"", "\"last4\":\"abcd\""));
        rejected(REQUEST.replace("\"cardType\":\"VISA\"", "\"cardType\":\"\""));
        for (String field : new String[] {"cardNumber", "PAN", "CVV"}) {
            rejected(REQUEST.replace("\"last4\":\"1111\"", "\"last4\":\"1111\",\"" + field + "\":\"4111111111111111\""));
        }
    }

    @Test
    void rejectsCallerSuppliedServerOwnedFields() throws Exception {
        for (String field : new String[] {"id", "orderId", "createdAt", "status", "totalPrice"}) {
            rejected(REQUEST.replaceFirst("\\{", "{\"" + field + "\":\"forged\","));
        }
    }

    @Test
    void rejectsDuplicateAndNonpositiveLineNumbers() throws Exception {
        rejected(REQUEST.replace("\"lineNumber\":2", "\"lineNumber\":1"));
        rejected(REQUEST.replace("\"lineNumber\":1", "\"lineNumber\":0"));
    }

    @Test
    void publishesOnlyOrderIdAfterPendingOrderIsPersisted() throws Exception {
        doAnswer(invocation -> {
            String json = invocation.getArgument(1);
            var tree = mapper.readTree(json);
            assertEquals(1, tree.size());
            String id = tree.get("orderId").asString();
            assertEquals(OrderStatus.PENDING, orders.findById(id).orElseThrow().status());
            return null;
        }).when(jms).convertAndSend(eq("petstore.order.submitted"),
                any(String.class));
        var response = createOrder(REQUEST);
        verify(jms).convertAndSend("petstore.order.submitted",
                mapper.writeValueAsString(new OrderSubmitted(response.orderId())));
    }

    @Test
    void publicationFailureLeavesPersistedOrderPending() {
        doThrow(new UncategorizedJmsException("Broker unavailable"))
                .when(jms).convertAndSend(anyString(), any(String.class));
        assertThrows(jakarta.servlet.ServletException.class, () -> createOrder(REQUEST));
        assertEquals(1, orders.count());
        assertEquals(OrderStatus.PENDING, orders.findAll().getFirst().status());
    }

    @Test
    void approvesSmallEnglishOrderAndDuplicateDeliveryPreservesSnapshot() throws Exception {
        var response = createOrder(REQUEST);
        var before = orders.findById(response.orderId()).orElseThrow();
        deliver(response.orderId());
        var approved = orders.findById(response.orderId()).orElseThrow();
        assertEquals(OrderStatus.APPROVED, approved.status());
        assertEquals(before.totalPrice(), approved.totalPrice());
        assertEquals(before.lineItems(), approved.lineItems());
        assertEquals(before.createdAt(), approved.createdAt());
        assertEquals(before.payment(), approved.payment());
        deliver(response.orderId());
        assertEquals(approved, orders.findById(response.orderId()).orElseThrow());
        verify(jms).convertAndSend(eq("petstore.notification.requested"), argThat((String json) ->
                mapper.readTree(json).get("notificationType").asString().equals("ORDER_APPROVED")));
        assertEquals(1, orders.count());
    }

    @Test
    void retainsLargeEnglishOrderPending() throws Exception {
        var response = createOrder(REQUEST.replace("0.10", "500.00"));
        deliver(response.orderId());
        assertEquals(OrderStatus.PENDING, orders.findById(response.orderId()).orElseThrow().status());
    }

    @Test
    void approvesSmallJapaneseOrder() throws Exception {
        var response = createOrder(REQUEST.replace("en-US", "ja-JP"));
        deliver(response.orderId());
        assertEquals(OrderStatus.APPROVED, orders.findById(response.orderId()).orElseThrow().status());
    }

    @Test
    void retainsUnsupportedLocalePending() throws Exception {
        var response = createOrder(REQUEST.replace("en-US", "zh-CN"));
        deliver(response.orderId());
        assertEquals(OrderStatus.PENDING, orders.findById(response.orderId()).orElseThrow().status());
    }

    @Test
    void ignoresMissingOrderAndMalformedEventsWithoutCreatingOrders() {
        listener.receive("{\"orderId\":\"unknown\"}");
        for (String json : new String[] {"{", "null", "{}", "{\"orderId\":null}", "{\"orderId\":\" \"}", "[]"}) {
            assertDoesNotThrow(() -> listener.receive(json));
        }
        assertDoesNotThrow(() -> listener.receive(null));
        assertEquals(0, orders.count());
    }

    @Test
    void concurrentDuplicateDeliveriesOnlyChangeStatus() throws Exception {
        var response = createOrder(REQUEST);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> deliver(response.orderId()));
            var second = executor.submit(() -> deliver(response.orderId()));
            first.get();
            second.get();
        }
        assertEquals(1, orders.count());
        assertEquals(OrderStatus.APPROVED, orders.findById(response.orderId()).orElseThrow().status());
        assertEquals(response.totalPrice(), orders.findById(response.orderId()).orElseThrow().totalPrice());
    }

    private OrderResponse createOrder(String body) throws Exception {
        var result = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsByteArray(), OrderResponse.class);
    }

    private void deliver(String id) {
        listener.receive(mapper.writeValueAsString(
                new OrderSubmitted(id)));
    }

    @Test
    void callerCannotSetInitialShippedQuantity() throws Exception {
        createOrder(REQUEST.replace("\"quantity\":3", "\"quantity\":3,\"quantityShipped\":3"));
        var order = orders.findAll().getFirst();
        assertEquals(OrderStatus.PENDING, order.status());
        assertTrue(order.lineItems().stream().allMatch(line -> line.quantityShipped() == 0));
    }

    private void rejected(String body) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, orders.count());
    }
}
