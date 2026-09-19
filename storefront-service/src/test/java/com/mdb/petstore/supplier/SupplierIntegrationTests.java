package com.mdb.petstore.supplier;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mdb.petstore.supplier.client.SupplierClient;
import com.mdb.petstore.identity.bootstrap.SupplierBootstrap;
import com.mdb.petstore.identity.model.Role;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;
import com.mdb.petstore.customer.repository.CustomerRepository;
import org.junit.jupiter.api.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"petstore.admin.bootstrap.username=", "petstore.admin.bootstrap.password=",
        "petstore.supplier.bootstrap.username=", "petstore.supplier.bootstrap.password="})
@AutoConfigureMockMvc
@Import(SupplierIntegrationTests.HttpStub.class)
class SupplierIntegrationTests {
    private static final String DATABASE = "petstore_supplier_ui_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ITEM = "{\"itemId\":\"EST-15\",\"quantity\":9,\"updatedAt\":\"2026-01-01T00:00:00Z\"}";
    private static final String ORDER = """
            {"orderId":"order-1","status":"PENDING","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z",
            "requestedLineCount":1,"shippedLineCount":0,"lines":[{"lineNumber":1,"itemId":"EST-15","quantityRequested":1,
            "quantityShipped":0,"remainingQuantity":1}],"shipments":[]}
            """;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @TestConfiguration static class HttpStub {
        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://supplier.test");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        @Bean MockRestServiceServer supplierServer() { return server; }
        @Bean @Primary SupplierClient testSupplierClient() { return new SupplierClient(builder.build()); }
    }
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("supplierServer") MockRestServiceServer server;
    @Autowired MongoTemplate mongo;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired PasswordEncoder encoder;
    @BeforeEach void reset() { assertEquals(DATABASE, mongo.getDb().getName()); server.reset(); users.deleteAll(); customers.deleteAll(); }
    @AfterEach void verifyCalls() { server.verify(); }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) { assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop(); }
    @Test void anonymousPagesRedirectAndApiRequiresAuthentication() throws Exception {
        for (String page : List.of("inventory", "orders"))
            mvc.perform(get("/supplier/" + page)).andExpect(status().is3xxRedirection()).andExpect(header().string("Location", endsWith("/login")));
        mvc.perform(get("/api/supplier/inventory")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/supplier/inventory/retry-pending").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void customerAndAdminAloneCannotUseAnySupplierRoute() throws Exception {
        for (String role : List.of("CUSTOMER", "ADMIN")) {
            for (String path : List.of("/supplier/inventory", "/supplier/orders", "/api/supplier/inventory", "/api/supplier/inventory/EST-15", "/api/supplier/orders", "/api/supplier/orders/order-1"))
                mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(put("/api/supplier/inventory/EST-15").with(user("other").roles(role)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/supplier/inventory/retry-pending").with(user("other").roles(role)).with(csrf())).andExpect(status().isForbidden());
        }
    }
    @Test void supplierPagesRenderControlsCsrfAndMinimalNavigation() throws Exception {
        mvc.perform(get("/supplier/inventory").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"inventory-filter\"")))
                .andExpect(content().string(containsString("id=\"supplier-inventory-table\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("id=\"supplier-orders-link\"")))
                .andExpect(content().string(not(containsString("id=\"admin-orders-link\""))))
                .andExpect(content().string(not(containsString("href=\"/account\""))));
        mvc.perform(get("/supplier/orders").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"fulfilment-status\"")))
                .andExpect(content().string(containsString("id=\"retry-pending\"")));
    }
    @Test void navigationIsRoleSpecificAndMultipleRolesShowBothTools() throws Exception {
        for (String role : List.of("CUSTOMER", "ADMIN"))
            mvc.perform(get("/shop").with(user("other").roles(role))).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("id=\"supplier-inventory-link\""))));
        mvc.perform(get("/shop").with(user("both").roles("SUPPLIER", "ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"admin-orders-link\"")))
                .andExpect(content().string(containsString("id=\"supplier-inventory-link\"")));
    }
    @Test void supplierCannotAccessAdminApis() throws Exception {
        mvc.perform(get("/api/admin/orders").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isForbidden());
    }
    @Test void inventoryListAndItemProxy() throws Exception {
        server.expect(requestTo("http://supplier.test/api/inventory")).andRespond(withSuccess("[" + ITEM + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://supplier.test/api/inventory/EST-15")).andRespond(withSuccess(ITEM, MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/supplier/inventory").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(9));
        mvc.perform(get("/api/supplier/inventory/EST-15").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value("EST-15"));
    }
    @Test void quantitySetForwardsExactValueAndPreservesPostAllocationQuantity() throws Exception {
        server.expect(requestTo("http://supplier.test/api/inventory/EST-15")).andExpect(method(HttpMethod.PUT))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"quantity\":10}"))
                .andRespond(withSuccess(ITEM, MediaType.APPLICATION_JSON));
        mvc.perform(put("/api/supplier/inventory/EST-15").with(user("supplier").roles("SUPPLIER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(9));
    }
    @Test void bothMutationsRequireCsrf() throws Exception {
        mvc.perform(put("/api/supplier/inventory/EST-15").with(user("supplier").roles("SUPPLIER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/supplier/inventory/retry-pending").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isForbidden());
    }
    @Test void retryPendingForwardsPostWithCsrf() throws Exception {
        server.expect(requestTo("http://supplier.test/api/inventory/retry-pending")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        mvc.perform(post("/api/supplier/inventory/retry-pending").with(user("supplier").roles("SUPPLIER")).with(csrf())).andExpect(status().isOk());
    }
    @Test void fulfimentListFilterAllAndDetailProxy() throws Exception {
        server.expect(requestTo("http://supplier.test/api/fulfilment/orders?status=PENDING")).andRespond(withSuccess("[" + ORDER + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://supplier.test/api/fulfilment/orders")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://supplier.test/api/fulfilment/orders/order-1")).andRespond(withSuccess(ORDER, MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/supplier/orders").param("status", "PENDING").with(user("supplier").roles("SUPPLIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].lines[0].remainingQuantity").value(1));
        mvc.perform(get("/api/supplier/orders").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/supplier/orders/order-1").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"));
    }
    @Test void negativeNullQuantitiesAndInvalidStatusAreRejectedLocally() throws Exception {
        for (String body : List.of("{\"quantity\":-1}", "{}", "{\"quantity\":null}"))
            mvc.perform(put("/api/supplier/inventory/EST-15").with(user("supplier").roles("SUPPLIER")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/supplier/orders").param("status", "APPROVED").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isBadRequest());
    }
    @Test void downstreamServerConnectionAndMutationFailuresAreSafe() throws Exception {
        server.expect(requestTo("http://supplier.test/api/inventory")).andRespond(withServerError().body("sensitive-internal-details"));
        server.expect(requestTo("http://supplier.test/api/inventory/EST-15")).andRespond(withException(new java.io.IOException("private-host")));
        server.expect(requestTo("http://supplier.test/api/inventory/retry-pending")).andRespond(withServerError());
        mvc.perform(get("/api/supplier/inventory").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("sensitive-internal-details"))));
        mvc.perform(put("/api/supplier/inventory/EST-15").with(user("supplier").roles("SUPPLIER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}")).andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("private-host"))));
        mvc.perform(post("/api/supplier/inventory/retry-pending").with(user("supplier").roles("SUPPLIER")).with(csrf())).andExpect(status().isBadGateway());
    }
    @Test void malformedOrUnconfirmedResponsesAreNotSuccessfulUpdates() throws Exception {
        server.expect(requestTo("http://supplier.test/api/inventory/EST-15")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://supplier.test/api/inventory/retry-pending")).andRespond(withStatus(HttpStatus.ACCEPTED));
        mvc.perform(put("/api/supplier/inventory/EST-15").with(user("supplier").roles("SUPPLIER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":10}")).andExpect(status().isBadGateway());
        mvc.perform(post("/api/supplier/inventory/retry-pending").with(user("supplier").roles("SUPPLIER")).with(csrf())).andExpect(status().isBadGateway());
    }
    @Test void notFoundAndValidationResponsesDoNotLeakBackendBodies() throws Exception {
        server.expect(requestTo("http://supplier.test/api/fulfilment/orders/missing")).andRespond(withStatus(HttpStatus.NOT_FOUND).body("private-body"));
        mvc.perform(get("/api/supplier/orders/missing").with(user("supplier").roles("SUPPLIER"))).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("private-body"))));
    }
    @Test void blankOrIncompleteBootstrapDoesNothing() {
        for (String[] pair : List.of(new String[]{"", ""}, new String[]{"supplier", ""}, new String[]{"", "secret"}))
            new SupplierBootstrap(users, encoder, pair[0], pair[1]).run(null);
        assertEquals(0, users.count()); assertEquals(0, customers.count());
    }
    @Test void bootstrapCreatesBcryptSupplierWithoutCustomerAndIsIdempotent() throws Exception {
        var bootstrap = new SupplierBootstrap(users, encoder, " Supplier-Demo ", "Supplier-test-password!");
        bootstrap.run(null);
        var supplier = users.findByUsername("supplier-demo").orElseThrow();
        assertEquals(Set.of(Role.SUPPLIER), supplier.getRoles()); assertTrue(supplier.isEnabled()); assertNull(supplier.getCustomerId());
        assertTrue(supplier.getPasswordHash().startsWith("$2")); assertTrue(encoder.matches("Supplier-test-password!", supplier.getPasswordHash()));
        bootstrap.run(null);
        assertEquals(1, users.count()); assertEquals(0, customers.count());
        assertEquals(supplier.getPasswordHash(), users.findById(supplier.getId()).orElseThrow().getPasswordHash());
        mvc.perform(post("/api/auth/login").param("username", "supplier-demo").param("password", "Supplier-test-password!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("ROLE_SUPPLIER")));
    }
    @Test void existingUserIsNeverPromotedOrOverwritten() {
        var existing = new User(); existing.setUsername("existing"); existing.setRoles(Set.of(Role.CUSTOMER));
        existing.setPasswordHash(encoder.encode("original")); existing.setEnabled(false); users.insert(existing);
        new SupplierBootstrap(users, encoder, "existing", "replacement").run(null);
        var after = users.findById(existing.getId()).orElseThrow();
        assertEquals(existing.getPasswordHash(), after.getPasswordHash()); assertEquals(existing.getRoles(), after.getRoles()); assertFalse(after.isEnabled());
    }
    @Test void registrationCannotAssignSupplier() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"customer","password":"Customer-test-password!","firstName":"Demo","lastName":"Buyer",
                "email":"demo@example.com","street1":"Demo Street","city":"Dublin","postalCode":"D01","country":"IE",
                "roles":["SUPPLIER"]}
                """)).andExpect(status().isCreated());
        assertEquals(Set.of(Role.CUSTOMER), users.findByUsername("customer").orElseThrow().getRoles());
    }
    @Test void loginScriptDefinesAdminThenSupplierThenShopPrecedence() throws Exception {
        String page = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString().replace("\\/", "/");
        assertTrue(page.indexOf("login.roles.includes('ROLE_ADMIN')") < page.indexOf("login.roles.includes('ROLE_SUPPLIER')"));
        assertTrue(page.indexOf("window.location.assign(supplierInventoryUrl)") < page.indexOf("window.location.assign(shopUrl)"));
        assertTrue(page.contains("/supplier/inventory")); assertTrue(page.contains("/admin/orders")); assertTrue(page.contains("/shop"));
    }
}
