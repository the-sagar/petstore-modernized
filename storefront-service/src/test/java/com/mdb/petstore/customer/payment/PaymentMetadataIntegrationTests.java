package com.mdb.petstore.customer.payment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mdb.petstore.customer.dto.UpdateAccountRequest;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"petstore.admin.bootstrap.username=", "petstore.admin.bootstrap.password="})
@AutoConfigureMockMvc
class PaymentMetadataIntegrationTests {
    private static final String DATABASE = "petstore_payment_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PAN = "4111111111111111";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired PaymentMetadataMigration migration;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @BeforeEach void reset() {
        assertEquals(DATABASE, mongo.getDb().getName());
        mongo.getCollection("customers").deleteMany(new Document()); users.deleteAll();
    }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }
    @Test void registrationPersistsOnlyDisplayMetadataAndKeepsBcrypt() throws Exception {
        register("4111 1111-1111 1111");
        var raw = raw();
        assertEquals("1111", card(raw).getString("last4"));
        assertFalse(raw.toJson().contains("cardNumber"));
        assertFalse(raw.toJson().contains(PAN));
        var user = users.findByUsername("buyer").orElseThrow();
        assertTrue(encoder.matches("Test-password-123!", user.getPasswordHash()));
        mvc.perform(get("/api/account").with(user("buyer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.last4").value("1111"))
                .andExpect(jsonPath("$.cardNumber").doesNotExist()).andExpect(content().string(not(containsString(PAN))));
    }
    @Test void blankRegistrationDoesNotStorePanOrInventLast4() throws Exception {
        register("   ");
        assertNull(card(raw()).get("last4"));
        assertFalse(raw().toJson().contains("cardNumber"));
    }
    @Test void replacementAndBlankUpdatePreserveSafeMetadataAndOtherFields() throws Exception {
        register(PAN);
        var update = request("5555-5555 5555-4444"); update.put("city", "Cork");
        mvc.perform(put("/api/account").with(user("buyer")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(update))).andExpect(status().isOk())
                .andExpect(jsonPath("$.last4").value("4444")).andExpect(jsonPath("$.city").value("Cork"))
                .andExpect(jsonPath("$.cardNumber").doesNotExist());
        update.put("cardNumber", " "); update.put("cardType", "MASTERCARD"); update.put("expiryDate", "11/2032");
        mvc.perform(put("/api/account").with(user("buyer")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(update))).andExpect(status().isOk())
                .andExpect(jsonPath("$.last4").value("4444"));
        assertEquals("4444", card(raw()).getString("last4"));
        assertEquals("MASTERCARD", card(raw()).getString("cardType"));
        assertEquals("11/2032", card(raw()).getString("expiryDate"));
        assertFalse(raw().toJson().contains("cardNumber"));
        assertFalse(raw().toJson().contains("5555555555554444"));
    }
    @Test void invalidRegistrationInputIsRejectedWithoutPersistingOrEchoingIt() throws Exception {
        for (var input : List.of("123", "abcd1111", "----", "41111111111111111111", "４１１１１１１１１１１１１１１１")) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request(input)))).andExpect(status().isBadRequest())
                    .andExpect(content().string(not(containsString(input))));
            assertEquals(0, mongo.getCollection("customers").countDocuments());
            assertEquals(0, users.count());
        }
    }
    @Test void invalidUpdateLeavesDocumentUnchanged() throws Exception {
        register(PAN); var before = raw();
        mvc.perform(put("/api/account").with(user("buyer")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request("not-a-card-1111"))))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("not-a-card-1111"))));
        assertEquals(before, raw());
    }
    @Test void paymentInputIsWriteOnlyForBothRequestDtos() {
        for (var type : List.of(RegisterRequest.class, UpdateAccountRequest.class)) {
            Object request = mapper.readValue("{\"cardNumber\":\"" + PAN + "\"}", type);
            assertFalse(mapper.writeValueAsString(request).contains("cardNumber"));
            assertFalse(mapper.writeValueAsString(request).contains(PAN));
        }
    }
    @Test void migrationPhysicallyRemovesPanPreservesOtherFieldsAndIsIdempotent() {
        var original = new Document("_id", "legacy").append("marker", "unchanged")
                .append("account", new Document("contactInfo", new Document("firstName", "Keep"))
                        .append("creditCard", new Document("cardType", "VISA").append("cardNumber", "4111-1111 1111 1111")
                                .append("expiryDate", "12/2030")));
        mongo.getCollection("customers").insertOne(original);
        migration.run(null);
        var after = raw(); var expected = Document.parse(original.toJson());
        card(expected).remove("cardNumber"); card(expected).put("last4", "1111");
        assertEquals(expected, after);
        assertFalse(after.toJson().contains("cardNumber"));
        migration.run(null); assertEquals(after, raw());
    }
    @Test void migrationLeavesModernDocumentsUntouched() {
        var modern = new Document("_id", "modern").append("account", new Document("creditCard",
                new Document("cardType", "VISA").append("last4", "9876").append("expiryDate", "12/2030")));
        mongo.getCollection("customers").insertOne(modern);
        migration.run(null); assertEquals(modern, raw());
    }
    @Test void migrationRemovesBlankMalformedAndNonStringLegacyValuesSafely() {
        for (Object invalid : java.util.Arrays.asList(" ", "invalid1234", 41111111L, null)) {
            mongo.getCollection("customers").deleteMany(new Document());
            mongo.getCollection("customers").insertOne(new Document("account", new Document("creditCard",
                    new Document("cardNumber", invalid).append("last4", "9876"))));
            migration.run(null);
            assertFalse(card(raw()).containsKey("cardNumber"));
            assertEquals("9876", card(raw()).getString("last4"));
        }
    }
    @Test void accountPageExplainsDisplayMetadataAndKeepsInputEmpty() throws Exception {
        mvc.perform(get("/account").with(user("buyer"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"saved-card\"")))
                .andExpect(content().string(containsString("Full card numbers are not stored")))
                .andExpect(content().string(not(containsString("Leaving it blank clears"))));
    }
    private void register(String number) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request(number)))).andExpect(status().isCreated())
                .andExpect(content().string(not(containsString(PAN))))
                .andExpect(jsonPath("$.cardNumber").doesNotExist());
    }
    private Map<String,Object> request(String number) {
        var data = new LinkedHashMap<String,Object>();
        data.put("username", "buyer"); data.put("password", "Test-password-123!");
        data.put("firstName", "Demo"); data.put("lastName", "Buyer"); data.put("email", "buyer@example.com");
        data.put("street1", "1 Demo Street"); data.put("city", "Dublin"); data.put("postalCode", "D01");
        data.put("country", "Ireland"); data.put("cardType", "VISA"); data.put("cardNumber", number);
        data.put("expiryDate", "12/2030"); return data;
    }
    private Document raw() { return mongo.getCollection("customers").find().first(); }
    private Document card(Document customer) { return customer.get("account", Document.class).get("creditCard", Document.class); }
}
