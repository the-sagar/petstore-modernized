package com.mdb.petstore.robustness;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdversarialStorefrontIntegrationTests {
    private static final String DATABASE = "petstore_adversarial_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper mapper;
    private Document originalItem;
    private static final String CONTACT = """
        {"firstName":"Test","lastName":"Customer","email":"adversarial@example.com",
         "street1":"Test Street","city":"Dublin","postalCode":"D01","country":"IE"}
        """;
    @BeforeEach void reset() {
        assertEquals(DATABASE, mongo.getDb().getName());
        originalItem = mongo.getCollection("items").find(new Document("_id", "EST-1")).first();
    }
    @AfterEach void restoreItem() {
        mongo.getCollection("items").replaceOne(new Document("_id", "EST-1"), originalItem);
    }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }
    private String register(String password) throws Exception {
        String username = "probe." + UUID.randomUUID();
        var body = mapper.readTree(CONTACT).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) body).put("username", username).put("password", password);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        return username;
    }
    @ParameterizedTest
    @ValueSource(strings = {"account", "account.contactInfo", "account.contactInfo.address", "account.creditCard", "profile"})
    void incompletePersistedAccountIsControlledAndNeverRepairedImplicitly(String field) throws Exception {
        String username = register("Test-password-123");
        var identity = mongo.getCollection("users").find(new Document("username", username)).first();
        var key = new Document("_id", identity.get("customerId"));
        // User.customerId is a String; Spring maps Customer's hex String ID to BSON ObjectId.
        key.put("_id", new org.bson.types.ObjectId(identity.getString("customerId")));
        mongo.getCollection("customers").updateOne(key, new Document("$unset", new Document(field, "")));
        var before = mongo.getCollection("customers").find(key).first();
        mvc.perform(get("/api/account").with(user(username).roles("CUSTOMER"))).andExpect(status().isConflict());
        mvc.perform(put("/api/account").with(user(username).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CONTACT)).andExpect(status().isConflict());
        assertEquals(before, mongo.getCollection("customers").find(key).first());
    }
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPPLIER", "CUSTOMER"})
    void identityWithoutCustomerHasControlledAccountOutcome(String role) throws Exception {
        mvc.perform(get("/api/account").with(user("missing-identity").roles(role))).andExpect(status().isNotFound());
        mvc.perform(put("/api/account").with(user("missing-identity").roles(role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CONTACT)).andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @ValueSource(strings = {"ascii", "unicode"})
    void oversizedBcryptInputIsValidationFailureNotDuplicateUsername(String kind) throws Exception {
        String password = kind.equals("ascii") ? "a".repeat(73) : "語".repeat(25);
        var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(CONTACT);
        body.put("username", "long." + UUID.randomUUID()).put("password", password);
        long users = mongo.getCollection("users").countDocuments(), customers = mongo.getCollection("customers").countDocuments();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertEquals(users, mongo.getCollection("users").countDocuments());
        assertEquals(customers, mongo.getCollection("customers").countDocuments());
    }
    @Test void bcryptExactUtf8BoundaryAndOptionalFieldsAreAccepted() throws Exception {
        String username = register("語".repeat(24));
        mvc.perform(post("/api/auth/login").param("username", username.toUpperCase(java.util.Locale.ROOT))
                .param("password", "語".repeat(24))).andExpect(status().isOk());
        mvc.perform(get("/api/account").with(user(username).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.street2").isEmpty()).andExpect(jsonPath("$.cardNumber").doesNotExist());
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "nullPrice", "negativePrice"})
    void unavailableCatalogPriceDoesNotCrashOrMutateCart(String kind) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(put("/api/cart/items/EST-1").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":3}")).andExpect(status().isOk());
        Document update = switch (kind) {
            case "missing" -> new Document("$unset", new Document("details", ""));
            case "empty" -> new Document("$set", new Document("details", List.of()));
            default -> new Document("$set", new Document("details.$[].listPrice",
                    kind.equals("nullPrice") ? null : new org.bson.types.Decimal128(new java.math.BigDecimal("-1"))));
        };
        mongo.getCollection("items").updateOne(new Document("_id", "EST-1"), update);
        mvc.perform(get("/api/cart").session(session)).andExpect(status().isConflict());
        mvc.perform(post("/api/cart/items").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"EST-1\"}")).andExpect(status().isConflict());
        mongo.getCollection("items").replaceOne(new Document("_id", "EST-1"), originalItem);
        mvc.perform(get("/api/cart").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }
    @Test void maximumQuantityUsesExactDecimalArithmetic() throws Exception {
        mvc.perform(put("/api/cart/items/EST-1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2147483647}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(35433480175.50));
    }
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{", "{}", "{\"quantity\":2147483648}", "{\"quantity\":null}"})
    void malformedCartInputIsControlled(String json) throws Exception {
        mvc.perform(put("/api/cart/items/EST-1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isBadRequest());
    }
    @Test void maximumPageAndMalformedPagingAreControlled() throws Exception {
        for (String path : List.of("/api/catalog/search?q=fish", "/api/catalog/categories/FISH/products", "/api/catalog/products/FI-SW-01/items")) {
            mvc.perform(get(path).param("page", "2147483647").param("size", "20"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
            for (String page : List.of("2147483648", "x", "1.5", "-1"))
                mvc.perform(get(path).param("page", page)).andExpect(status().isBadRequest());
        }
    }
}
