package com.mdb.petstore.robustness;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicErrorResponseIntegrationTests {
    private static final String DATABASE = "petstore_error_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @LocalServerPort int port;
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }
    @Test
    void publicApiErrorsKeepStatusAcrossRealServletErrorDispatch() throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            for (var entry : Map.of("/api/catalog/items/MISSING", 404,
                    "/api/catalog/categories/MISSING", 404,
                    "/api/catalog/search?q=fish&page=-1", 400,
                    "/api/catalog/search?q=fish&size=abc", 400).entrySet()) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + entry.getKey()))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(entry.getValue(), response.statusCode(), entry.getKey());
                assertTrue(response.headers().firstValue("Location").isEmpty());
                assertFalse(response.body().contains("java.lang."));
                assertFalse(response.body().contains("stackTrace"));
            }
            // Only ERROR dispatch is public; direct requests still obey the normal security boundary.
            for (String path : new String[] {"/error", "/account", "/admin/orders", "/supplier/inventory"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(302, response.statusCode(), path);
            }
        }
    }
}
