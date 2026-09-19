package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import com.mdb.petstore.orderprocessing.order.dto.OrderResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
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

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderRepository orders;
    @Autowired private MongoTemplate mongo;

    @BeforeEach
    void resetOrders() {
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

    private void rejected(String body) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, orders.count());
    }
}
