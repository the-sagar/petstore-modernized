package com.mdb.petstore.supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jms.listener.auto-startup=false", "petstore.inventory.seed-enabled=false"})
class SupplierApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void startsWebServerWithOwnedDatabase() {
        assertTrue(port > 0);
        assertEquals("petstore_supplier", mongoTemplate.getDb().getName());
    }
}
