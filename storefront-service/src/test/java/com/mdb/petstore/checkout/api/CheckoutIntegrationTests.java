package com.mdb.petstore.checkout.api;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.mdb.petstore.cart.dto.CartResponse;
import com.mdb.petstore.cart.model.ShoppingCart;
import com.mdb.petstore.catalog.repository.ItemRepository;
import com.mdb.petstore.checkout.client.CreateOrderRequest;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.service.RegistrationService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CheckoutIntegrationTests.HttpStub.class)
class CheckoutIntegrationTests {

    private static final String DATABASE = "petstore_checkout_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String RAW_CARD = "4111111111111111";
    private static final String REQUEST = """
            {"billingInfo":{"firstName":"Bill","lastName":"Buyer","email":"billing@example.com","phone":"555-0100",
              "street1":"1 Billing St","city":"Dublin","postalCode":"D01","country":"IE"},
             "shippingInfo":{"firstName":"Ship","lastName":"Recipient","email":"shipping@example.com","phone":"555-0200",
              "street1":"2 Shipping St","city":"Cork","postalCode":"C01","country":"IE"}}
            """;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }

    @TestConfiguration
    static class HttpStub {
        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://order.test");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        MockRestServiceServer orderServer() {
            return server;
        }

        @Bean
        @Primary
        RestClient mockOrderRestClient() {
            return builder.build();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired @Qualifier("orderServer") private MockRestServiceServer server;
    @Autowired private ObjectMapper mapper;
    @Autowired private RegistrationService registration;
    @Autowired private ItemRepository items;
    @Autowired private com.mdb.petstore.customer.repository.CustomerRepository customers;
    @Autowired private MongoTemplate mongo;

    private MockHttpSession session;
    private User user;
    private String email;

    @BeforeEach
    void registerAndLogin() throws Exception {
        server.reset();
        session = new MockHttpSession();
        String username = "checkout." + UUID.randomUUID();
        email = username + "@example.com";
        var request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("Checkout-test-password-123!");
        request.setFirstName("Account");
        request.setLastName("Owner");
        request.setEmail(email);
        request.setStreet1("Account Street");
        request.setCity("Dublin");
        request.setPostalCode("D02");
        request.setCountry("IE");
        request.setCardType("VISA");
        request.setCardNumber(RAW_CARD);
        request.setExpiryDate("12/2030");
        user = registration.register(request);
        mvc.perform(post("/api/auth/login").session(session)
                        .param("username", username).param("password", request.getPassword()))
                .andExpect(status().isOk());
    }

    @AfterEach
    void verifyHttpCalls() {
        server.verify();
    }

    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName());
        mongo.getDb().drop();
    }

    @Test
    void anonymousCheckoutWithCsrfRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/checkout").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCheckoutWithoutCsrfIsForbiddenAndPreservesCart() throws Exception {
        putItem("EST-1", 2);
        var original = cart();
        mvc.perform(post("/api/checkout").session(session).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertEquals(original, cart());
    }

    @Test
    void missingPaymentMetadataRejectsCheckoutWithoutClearingCart() throws Exception {
        putItem("EST-1", 1);
        var original = cart();
        var customer = customers.findById(user.getCustomerId()).orElseThrow();
        customer.getAccount().getCreditCard().setLast4(null);
        customers.save(customer);
        mvc.perform(checkout("en-US")).andExpect(status().isConflict());
        assertEquals(original, cart());
    }

    @Test
    void emptyAuthenticatedCartIsRejectedWithoutHttpCall() throws Exception {
        mvc.perform(checkout("en-US")).andExpect(status().isBadRequest());
    }

    @Test
    void sendsServerIdentityCurrentPricesAndQuantitiesAndClearsOnlyAfter201() throws Exception {
        // Checkout must use saved display metadata directly, with no persisted PAN to derive it from.
        var customer = customers.findById(user.getCustomerId()).orElseThrow();
        customer.getAccount().getCreditCard().setLast4("9876");
        customers.save(customer);
        var raw = mongo.getCollection("customers").find(new org.bson.Document("account.contactInfo.email", email)).first();
        assertNotNull(raw);
        assertFalse(raw.toJson().contains("cardNumber"));
        assertFalse(raw.toJson().contains(RAW_CARD));
        putItem("EST-15", 2);
        putItem("EST-1", 3);
        var catalogItem = items.findById("EST-1").orElseThrow();
        var details = catalogItem.getDetails().stream().filter(d -> d.getLocale().equals("en-US")).findFirst().orElseThrow();
        var originalPrice = details.getListPrice();
        try {
            details.setListPrice(new BigDecimal("0.10"));
            items.save(catalogItem);
            server.expect(requestTo("http://order.test/api/orders")).andExpect(method(HttpMethod.POST))
                    .andExpect(request -> {
                        String json = ((MockClientHttpRequest) request).getBodyAsString();
                        assertFalse(json.contains(RAW_CARD));
                        assertFalse(json.contains("cardNumber"));
                        assertFalse(json.contains("expiryDate"));
                        assertFalse(json.contains("CVV"));
                        var tree = mapper.readTree(json);
                        assertEquals(2, tree.get("payment").size());
                        assertEquals("VISA", tree.get("payment").get("cardType").asText());
                        assertEquals("9876", tree.get("payment").get("last4").asText());
                        for (String field : List.of("orderId", "id", "createdAt", "status", "totalPrice")) {
                            assertFalse(tree.has(field));
                        }
                        var outbound = mapper.readValue(json, CreateOrderRequest.class);
                        assertEquals(user.getCustomerId(), outbound.customerId());
                        assertEquals(user.getUsername(), outbound.username());
                        assertEquals(email, outbound.email());
                        assertEquals("en-US", outbound.locale());
                        assertEquals("1 Billing St", outbound.billingInfo().street1());
                        assertEquals("2 Shipping St", outbound.shippingInfo().street1());
                        assertEquals(2, outbound.lineItems().size());
                        var first = outbound.lineItems().getFirst();
                        assertEquals(1, first.lineNumber());
                        assertEquals("EST-1", first.itemId());
                        assertEquals("FI-SW-01", first.productId());
                        assertEquals("FISH", first.categoryId());
                        assertEquals(3, first.quantity());
                        assertEquals(new BigDecimal("0.10"), first.unitPrice());
                        assertEquals(2, outbound.lineItems().get(1).lineNumber());
                        assertEquals("EST-15", outbound.lineItems().get(1).itemId());
                        assertEquals(2, outbound.lineItems().get(1).quantity());
                        var state = (ShoppingCart) session.getAttribute("scopedTarget.shoppingCart");
                        assertEquals(2, state.getQuantities().size());
                    }).andRespond(created("47.30"));
            mvc.perform(checkout("en-US")).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value("order-created"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.totalPrice").value(47.30));
            assertEquals(0, cart().lineCount());
        } finally {
            details.setListPrice(originalPrice);
            items.save(catalogItem);
        }
    }

    @Test
    void capturesJapanesePriceFromCatalog() throws Exception {
        putItem("EST-1", 2);
        server.expect(requestTo("http://order.test/api/orders")).andExpect(request -> {
            var outbound = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), CreateOrderRequest.class);
            assertEquals("ja-JP", outbound.locale());
            assertEquals(new BigDecimal("1951"), outbound.lineItems().getFirst().unitPrice());
        }).andRespond(created("3902"));
        mvc.perform(checkout(" ja_jp ")).andExpect(status().isCreated());
        assertEquals(0, cart().lineCount());
    }

    @Test
    void preservesEst15JapaneseFallback() throws Exception {
        putItem("EST-15", 2);
        server.expect(requestTo("http://order.test/api/orders")).andExpect(request -> {
            var outbound = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), CreateOrderRequest.class);
            assertEquals("ja-JP", outbound.locale());
            assertEquals(new BigDecimal("23.50"), outbound.lineItems().getFirst().unitPrice());
        }).andRespond(created("47.00"));
        mvc.perform(checkout("ja-JP")).andExpect(status().isCreated());
    }

    @Test
    void downstream4xxLeavesCartUnchanged() throws Exception {
        failedCheckout(withStatus(HttpStatus.BAD_REQUEST).body("Remote private details"));
    }

    @Test
    void downstream5xxLeavesCartUnchanged() throws Exception {
        failedCheckout(withServerError());
    }

    @Test
    void connectionFailureLeavesCartUnchanged() throws Exception {
        failedCheckout(withException(new IOException("Connection refused")));
    }

    @Test
    void timeoutLeavesCartUnchanged() throws Exception {
        failedCheckout(withException(new SocketTimeoutException("Read timed out")));
    }

    @Test
    void malformedResponseLeavesCartUnchanged() throws Exception {
        failedCheckout(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("not-json"));
    }

    @Test
    void emptyOrIncomplete201ResponseLeavesCartUnchanged() throws Exception {
        failedCheckout(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));
        server.reset();
        failedCheckout(withStatus(HttpStatus.CREATED));
    }

    @Test
    void unexpected200ResponseLeavesCartUnchanged() throws Exception {
        failedCheckout(withSuccess(successJson("33.00"), MediaType.APPLICATION_JSON));
    }

    @Test
    void incorrectTotalOrStatusLeavesCartUnchanged() throws Exception {
        failedCheckout(created("999.99"));
        server.reset();
        failedCheckout(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body(successJson("33.00").replace("PENDING", "APPROVED")));
    }

    @Test
    void browserCannotSupplyIdentityPricesQuantitiesOrRawPayment() throws Exception {
        putItem("EST-1", 2);
        var original = cart();
        for (String field : List.of("username", "customerId", "lineItems", "totalPrice", "cardNumber", "status")) {
            mvc.perform(checkout("en-US").content(REQUEST.replaceFirst("\\{", "{\"" + field + "\":\"forged\",")))
                    .andExpect(status().isBadRequest());
            assertEquals(original, cart());
        }
    }

    @Test
    void invalidContactIsRejectedWithoutHttpCallAndPreservesCart() throws Exception {
        putItem("EST-1", 2);
        var original = cart();
        mvc.perform(checkout("en-US").content(REQUEST.replace("billing@example.com", "invalid")))
                .andExpect(status().isBadRequest());
        assertEquals(original, cart());
    }

    private void failedCheckout(ResponseCreator response) throws Exception {
        putItem("EST-1", 2);
        var original = cart();
        server.expect(requestTo("http://order.test/api/orders")).andRespond(response);
        var result = mvc.perform(checkout("en-US")).andExpect(status().isBadGateway()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("Remote private details"));
        assertEquals(original, cart());
        server.verify();
    }

    private MockHttpServletRequestBuilder checkout(String locale) {
        return post("/api/checkout").session(session).with(csrf()).param("locale", locale)
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST);
    }

    private void putItem(String id, int quantity) throws Exception {
        mvc.perform(put("/api/cart/items/" + id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private CartResponse cart() throws Exception {
        var response = mvc.perform(get("/api/cart").session(session)).andExpect(status().isOk()).andReturn();
        return mapper.readValue(response.getResponse().getContentAsByteArray(), CartResponse.class);
    }

    private static ResponseCreator created(String total) {
        return withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(successJson(total));
    }

    private static String successJson(String total) {
        return "{\"orderId\":\"order-created\",\"status\":\"PENDING\",\"createdAt\":\"2026-09-19T12:00:00Z\",\"totalPrice\":" + total + "}";
    }
}
