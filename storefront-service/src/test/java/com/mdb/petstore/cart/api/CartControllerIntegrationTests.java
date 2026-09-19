package com.mdb.petstore.cart.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.mdb.petstore.cart.dto.CartResponse;
import com.mdb.petstore.cart.model.ShoppingCart;
import com.mdb.petstore.catalog.repository.ItemRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerIntegrationTests {

    private static final String DATABASE = "petstore_cart_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolateDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }

    @AfterAll
    static void cleanUpIsolatedDatabase(@Autowired MongoTemplate template) {
        assertEquals(DATABASE, template.getDb().getName());
        template.getDb().drop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository items;

    @Test
    void anonymousGetWithoutCsrfReturnsEmptyCart() throws Exception {
        assertEmpty(response(get("/api/cart")));
    }

    @Test
    void anonymousAddWithCsrfCreatesQuantityOneAndCatalogDisplayData() throws Exception {
        var session = new MockHttpSession();
        var cart = add(session, "EST-1", "en-US");
        assertEquals(1, cart.lineCount());
        var line = cart.items().getFirst();
        assertEquals("EST-1", line.itemId());
        assertEquals("FI-SW-01", line.productId());
        assertEquals("FISH", line.categoryId());
        assertEquals("Angelfish", line.productName());
        assertEquals(List.of("Large", "Cuddly"), line.attributes());
        assertEquals("Fresh Water fish from Japan", line.description());
        assertEquals("fish3.gif", line.image());
        assertEquals(1, line.quantity());
        assertMoney("16.50", line.unitPrice());
        assertMoney("16.50", line.lineTotal());
        assertMoney("16.50", cart.subtotal());
    }

    @Test
    void sameSessionRetainsCartAndOnlyIdsAndQuantitiesAreStored() throws Exception {
        var session = new MockHttpSession();
        var added = add(session, "EST-1", "en-US");
        assertEquals(added, response(get("/api/cart").session(session)));
        var state = (ShoppingCart) session.getAttribute("scopedTarget.shoppingCart");
        assertNotNull(state);
        assertEquals(java.util.Map.of("EST-1", 1), state.getQuantities());
    }

    @Test
    void differentSessionsHaveIndependentCarts() throws Exception {
        var first = new MockHttpSession();
        var second = new MockHttpSession();
        add(first, "EST-1", "en-US");
        assertEmpty(response(get("/api/cart").session(second)));
        add(second, "EST-15", "en-US");
        assertEquals("EST-1", response(get("/api/cart").session(first)).items().getFirst().itemId());
        assertEquals("EST-15", response(get("/api/cart").session(second)).items().getFirst().itemId());
    }

    @Test
    void repeatedAddKeepsQuantityOneAndResetsAnUpdatedQuantity() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        var repeated = add(session, "EST-1", "en-US");
        assertEquals(1, repeated.lineCount());
        assertEquals(1, repeated.items().getFirst().quantity());
        update(session, "EST-1", 7, "en-US");
        assertEquals(1, add(session, "EST-1", "en-US").items().getFirst().quantity());
    }

    @Test
    void updateSetsExactQuantityRatherThanIncrementing() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        assertEquals(3, update(session, "EST-1", 3, "en-US").items().getFirst().quantity());
        assertEquals(2, update(session, "EST-1", 2, "en-US").items().getFirst().quantity());
        assertEquals(2, response(get("/api/cart").session(session)).items().getFirst().quantity());
    }

    @Test
    void positiveUpdateCanSetQuantityForAnItemNotYetInTheCart() throws Exception {
        var cart = update(new MockHttpSession(), "EST-1", 3, "en-US");
        assertEquals(1, cart.lineCount());
        assertEquals(3, cart.items().getFirst().quantity());
    }

    @Test
    void zeroQuantityRemovesItem() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        assertEmpty(update(session, "EST-1", 0, "en-US"));
        assertEmpty(response(get("/api/cart").session(session)));
    }

    @Test
    void negativeQuantityRemovesItem() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        assertEmpty(update(session, "EST-1", -5, "en-US"));
    }

    @Test
    void deleteRemovesItemAndIsIdempotentIncludingUnknownIds() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        assertEmpty(response(delete("/api/cart/items/EST-1").session(session).with(csrf())));
        assertEmpty(response(delete("/api/cart/items/EST-1").session(session).with(csrf())));
        assertEmpty(response(delete("/api/cart/items/missing").session(session).with(csrf())));
    }

    @Test
    void calculatesMultipleLineTotalsAndSubtotalUsingDecimalPrices() throws Exception {
        var session = new MockHttpSession();
        update(session, "EST-15", 2, "en-US");
        var cart = update(session, "EST-1", 3, "en-US");
        assertEquals(2, cart.lineCount());
        assertEquals(List.of("EST-1", "EST-15"), cart.items().stream().map(line -> line.itemId()).toList());
        assertMoney("49.50", cart.items().get(0).lineTotal());
        assertMoney("47.00", cart.items().get(1).lineTotal());
        assertMoney("96.50", cart.subtotal());
    }

    @Test
    void localeChangesResolveCurrentPricesAndNamesWithoutChangingState() throws Exception {
        var session = new MockHttpSession();
        var japanese = add(session, "EST-1", "ja-JP").items().getFirst();
        assertEquals("エンゼルフィッシュ", japanese.productName());
        assertEquals(List.of("大", "優しい"), japanese.attributes());
        assertMoney("1951", japanese.unitPrice());
        var chinese = update(session, "EST-1", 2, "zh-CN").items().getFirst();
        assertEquals("天使鱼", chinese.productName());
        assertEquals("日本产的淡水鱼", chinese.description());
        assertMoney("142", chinese.unitPrice());
        assertMoney("284", chinese.lineTotal());
        var english = response(get("/api/cart").session(session)).items().getFirst();
        assertEquals(2, english.quantity());
        assertEquals("Angelfish", english.productName());
        assertMoney("33.00", english.lineTotal());
        add(session, "EST-15", "en-US");
        var afterDelete = response(delete("/api/cart/items/EST-15").session(session)
                .param("locale", "ja-JP").with(csrf()));
        assertMoney("3902", afterDelete.subtotal());
    }

    @Test
    void est15UsesExistingJapaneseFallbackForItemDetailsAndPrice() throws Exception {
        var session = new MockHttpSession();
        var line = add(session, "EST-15", "ja-JP").items().getFirst();
        assertEquals("マンクスネコ", line.productName());
        assertEquals("Great for reducing mouse populations", line.description());
        assertEquals(List.of("With tail"), line.attributes());
        assertMoney("23.50", line.unitPrice());
        assertEquals(line, response(get("/api/cart").session(session).param("locale", "ja-JP"))
                .items().getFirst());
    }

    @Test
    void unknownAddAndUpdateReturn404WithoutMutatingExistingCart() throws Exception {
        var session = new MockHttpSession();
        var original = add(session, "EST-1", "en-US");
        mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"missing\"}"))
                .andExpect(status().isNotFound());
        assertEquals(original, response(get("/api/cart").session(session)));
        for (int quantity : new int[] {3, 0, -1}) {
            mockMvc.perform(put("/api/cart/items/missing").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                    .andExpect(status().isNotFound());
            assertEquals(original, response(get("/api/cart").session(session)));
        }
    }

    @Test
    void allCartMutationsWithoutCsrfReturn403AndLeaveCartUnchanged() throws Exception {
        var session = new MockHttpSession();
        var original = add(session, "EST-1", "en-US");
        mockMvc.perform(post("/api/cart/items").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"EST-15\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/cart/items/EST-1").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/cart/items/EST-1").session(session)).andExpect(status().isForbidden());
        assertEquals(original, response(get("/api/cart").session(session)));
    }

    @Test
    void missingOrBlankFieldsReturn400WithoutMutatingCart() throws Exception {
        var session = new MockHttpSession();
        var original = add(session, "EST-1", "en-US");
        for (String body : List.of("{}", "{\"itemId\":null}", "{\"itemId\":\" \"}")) {
            mockMvc.perform(post("/api/cart/items").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"quantity\":null}")) {
            mockMvc.perform(put("/api/cart/items/EST-1").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(original, response(get("/api/cart").session(session)));
    }

    @Test
    void cartReadsCurrentCatalogPricesInsteadOfSessionSnapshots() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        var item = items.findById("EST-1").orElseThrow();
        var detail = item.getDetails().stream().filter(d -> d.getLocale().equals("en-US")).findFirst().orElseThrow();
        BigDecimal original = detail.getListPrice();
        try {
            detail.setListPrice(new BigDecimal("0.10"));
            items.save(item);
            assertMoney("0.10", response(get("/api/cart").session(session)).items().getFirst().unitPrice());
            assertMoney("0.30", update(session, "EST-1", 3, "en-US").subtotal());
        } finally {
            detail.setListPrice(original);
            items.save(item);
        }
    }

    @Test
    void accountAccessRemainsProtectedAndCatalogRemainsPublic() throws Exception {
        var session = new MockHttpSession();
        add(session, "EST-1", "en-US");
        mockMvc.perform(get("/api/account").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/account").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/catalog/categories").session(session)).andExpect(status().isOk());
    }

    private CartResponse add(MockHttpSession session, String id, String locale) throws Exception {
        return response(post("/api/cart/items").session(session).with(csrf()).param("locale", locale)
                .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"" + id + "\"}"));
    }

    private CartResponse update(MockHttpSession session, String id, int quantity, String locale) throws Exception {
        return response(put("/api/cart/items/" + id).session(session).with(csrf()).param("locale", locale)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"));
    }

    private CartResponse response(MockHttpServletRequestBuilder request) throws Exception {
        var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), CartResponse.class);
    }

    private static void assertEmpty(CartResponse cart) {
        assertEquals(List.of(), cart.items());
        assertEquals(0, cart.lineCount());
        assertMoney("0", cart.subtotal());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
