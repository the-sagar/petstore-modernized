package com.mdb.petstore.admin;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mdb.petstore.identity.bootstrap.AdminBootstrap;
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
import org.springframework.mock.web.MockHttpSession;
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

@SpringBootTest(properties = {"petstore.admin.bootstrap.username=", "petstore.admin.bootstrap.password="})
@AutoConfigureMockMvc
@Import(AdminIntegrationTests.HttpStub.class)
class AdminIntegrationTests {
    private static final String DATABASE = "petstore_admin_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ORDER = """
            {"orderId":"order-1","username":"buyer","createdAt":"2026-01-01T00:00:00Z","locale":"en-US",
            "status":"PENDING","totalPrice":511.50,"lines":[{"lineNumber":1,"itemId":"EST-1","productId":"FI-SW-01",
            "categoryId":"FISH","quantity":31,"quantityShipped":0,"unitPrice":16.50}]}
            """;
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @TestConfiguration
    static class HttpStub {
        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://order.test");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        @Bean MockRestServiceServer adminServer() { return server; }
        @Bean @Primary RestClient mockAdminRestClient() { return builder.build(); }
    }
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("adminServer") MockRestServiceServer server;
    @Autowired MongoTemplate mongo;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired PasswordEncoder encoder;
    @BeforeEach
    void reset() {
        assertEquals(DATABASE, mongo.getDb().getName());
        server.reset(); users.deleteAll(); customers.deleteAll();
    }
    @AfterEach void verifyCalls() { server.verify(); }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }
    @Test
    void anonymousAdminPageRedirectsAndApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/admin/orders")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/orders/order-1/approve").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test
    void customerCannotAccessAdminPageOrAnyAdminApi() throws Exception {
        mvc.perform(get("/admin/orders").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        for (String path : List.of("/api/admin/orders", "/api/admin/orders/order-1"))
            mvc.perform(get(path).with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        for (String action : List.of("approve", "deny"))
            mvc.perform(post("/api/admin/orders/order-1/" + action).with(user("customer").roles("CUSTOMER")).with(csrf()))
                    .andExpect(status().isForbidden());
    }
    @Test
    void adminPageHasFilterTableCsrfAndRoleAwareNavigation() throws Exception {
        mvc.perform(get("/admin/orders").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(view().name("admin-orders"))
                .andExpect(content().string(containsString("id=\"order-status\"")))
                .andExpect(content().string(containsString("id=\"admin-order-rows\"")))
                .andExpect(content().string(containsString("id=\"admin-orders-link\"")))
                .andExpect(content().string(containsString("id=\"admin-logout-form\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("href=\"/account\""))));
        mvc.perform(get("/shop").with(user("customer").roles("CUSTOMER"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"admin-orders-link\""))));
        mvc.perform(get("/shop")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"admin-orders-link\""))));
    }
    @Test
    void adminListsFilteredOrdersThroughProxy() throws Exception {
        server.expect(requestTo("http://order.test/api/admin/orders?status=PENDING")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + ORDER + "]", MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/admin/orders").param("status", "PENDING").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].orderId").value("order-1"));
    }
    @Test
    void adminListsAllAndGetsDetailsThroughProxy() throws Exception {
        server.expect(requestTo("http://order.test/api/admin/orders")).andRespond(withSuccess("[" + ORDER + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://order.test/api/admin/orders/order-1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ORDER, MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/admin/orders").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders/order-1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].quantity").value(31));
    }
    @Test
    void bothAdminMutationsRequireCsrf() throws Exception {
        for (String action : List.of("approve", "deny"))
            mvc.perform(post("/api/admin/orders/order-1/" + action).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isForbidden());
    }
    @Test
    void approveAndDenyForwardPostWithCsrf() throws Exception {
        for (String action : List.of("approve", "deny")) {
            server.reset();
            String status = action.equals("approve") ? "APPROVED" : "DENIED";
            server.expect(requestTo("http://order.test/api/admin/orders/order-1/" + action)).andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(ORDER.replace("PENDING", status), MediaType.APPLICATION_JSON));
            mvc.perform(post("/api/admin/orders/order-1/" + action).with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
            server.verify();
        }
    }
    @Test
    void supplierMayHaveAlreadyCompletedSuccessfulApproval() throws Exception {
        server.expect(requestTo("http://order.test/api/admin/orders/order-1/approve"))
                .andRespond(withSuccess(ORDER.replace("PENDING", "COMPLETED"), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/admin/orders/order-1/approve").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }
    @Test
    void proxyPreservesNotFoundAndConflictWithoutRemoteBody() throws Exception {
        for (var code : List.of(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)) {
            server.reset();
            server.expect(requestTo("http://order.test/api/admin/orders/order-1/approve"))
                    .andRespond(withStatus(code).body("sensitive remote detail"));
            mvc.perform(post("/api/admin/orders/order-1/approve").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is(code.value())).andExpect(content().string(not(containsString("sensitive"))));
            server.verify();
        }
    }
    @Test
    void downstreamServerAndConnectionFailuresAreSafe502() throws Exception {
        for (boolean connection : List.of(false, true)) {
            server.reset();
            var expectation = server.expect(requestTo("http://order.test/api/admin/orders"));
            if (connection) expectation.andRespond(withException(new java.io.IOException("private connection detail")));
            else expectation.andRespond(withServerError().body("private downstream detail"));
            mvc.perform(get("/api/admin/orders").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadGateway()).andExpect(content().string(not(containsString("private"))));
            server.verify();
        }
    }
    @Test
    void malformedOrUnconfirmedDecisionDoesNotBecomeSuccess() throws Exception {
        for (String body : List.of("{", "{}", ORDER, ORDER.replace("order-1", "wrong-id"))) {
            server.reset();
            server.expect(requestTo("http://order.test/api/admin/orders/order-1/approve"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            mvc.perform(post("/api/admin/orders/order-1/approve").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isBadGateway());
            server.verify();
        }
    }
    @Test
    void invalidStatusIsRejectedBeforeDownstreamCall() throws Exception {
        mvc.perform(get("/api/admin/orders").param("status", "INVALID").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }
    @Test
    void blankOrIncompleteBootstrapDoesNothing() {
        for (var pair : List.of(List.of("", ""), List.of("admin", ""), List.of("", "secret")))
            new AdminBootstrap(users, encoder, pair.get(0), pair.get(1)).run(null);
        assertEquals(0, users.count()); assertEquals(0, customers.count());
    }
    @Test
    void configuredBootstrapStoresOnlyBcryptCreatesNoCustomerAndIsIdempotent() {
        String password = UUID.randomUUID().toString();
        var bootstrap = new AdminBootstrap(users, encoder, "  Local.Admin  ", password);
        bootstrap.run(null);
        var user = users.findByUsername("local.admin").orElseThrow();
        assertEquals(Set.of(Role.ADMIN), user.getRoles()); assertTrue(user.isEnabled());
        assertNull(user.getCustomerId()); assertNotNull(user.getCreatedAt());
        assertTrue(user.getPasswordHash().startsWith("$2"));
        assertTrue(encoder.matches(password, user.getPasswordHash()));
        assertFalse(mongo.getCollection("users").find().first().toJson().contains(password));
        bootstrap.run(null);
        assertEquals(1, users.count()); assertEquals(0, customers.count());
        assertEquals(user.getPasswordHash(), users.findByUsername("local.admin").orElseThrow().getPasswordHash());
    }
    @Test
    void existingUsernameIsNeverPromotedOrPasswordOverwritten() {
        var original = new User();
        original.setUsername("existing"); original.setRoles(Set.of(Role.CUSTOMER));
        original.setEnabled(false); original.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        users.insert(original);
        new AdminBootstrap(users, encoder, "EXISTING", UUID.randomUUID().toString()).run(null);
        var unchanged = users.findByUsername("existing").orElseThrow();
        assertEquals(original.getId(), unchanged.getId()); assertEquals(original.getPasswordHash(), unchanged.getPasswordHash());
        assertEquals(Set.of(Role.CUSTOMER), unchanged.getRoles()); assertFalse(unchanged.isEnabled());
    }
    @Test
    void bootstrappedAdminCanLoginWithoutCustomerAndNavigateToAdmin() throws Exception {
        String password = UUID.randomUUID().toString();
        new AdminBootstrap(users, encoder, "local.admin", password).run(null);
        var session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).param("username", "local.admin").param("password", password))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mvc.perform(get("/admin/orders").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("login.roles.includes('ROLE_ADMIN')")))
                .andExpect(content().string(containsString("window.location.assign(adminOrdersUrl)")))
                .andExpect(content().string(containsString("window.location.assign(shopUrl)")));
    }
    @Test
    void publicRegistrationCannotSelfAssignAdmin() throws Exception {
        String password = UUID.randomUUID().toString();
        String request = """
                {"username":"ordinary","password":"%s","roles":["ADMIN"],"firstName":"Demo","lastName":"Buyer",
                 "email":"demo@example.com","street1":"1 Street","city":"Dublin","postalCode":"D01","country":"IE"}
                """.formatted(password);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles", contains("CUSTOMER")));
        assertEquals(Set.of(Role.CUSTOMER), users.findByUsername("ordinary").orElseThrow().getRoles());
    }
}
