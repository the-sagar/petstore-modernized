package com.mdb.petstore.supplier.inventory.seed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import com.mdb.petstore.supplier.inventory.model.Inventory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "petstore.inventory.seed-enabled", havingValue = "true", matchIfMissing = true)
public class InventorySeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InventorySeeder.class);
    private final MongoTemplate mongo;
    public InventorySeeder(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var records = new ArrayList<Inventory>();
        var ids = new HashSet<String>();
        try (var input = new ClassPathResource("legacy/inventory.xml").getInputStream()) {
            var nodes = factory.newDocumentBuilder().parse(input).getElementsByTagName("Inventory");
            for (int i = 0; i < nodes.getLength(); i++) {
                var element = (org.w3c.dom.Element) nodes.item(i);
                String id = element.getAttribute("id");
                int quantity = Integer.parseInt(element.getAttribute("quantity"));
                if (!id.matches("EST-([1-9]|1[0-9]|2[0-9])") || !ids.add(id) || quantity != 10000)
                    throw new IllegalStateException("Invalid legacy inventory seed");
                records.add(new Inventory(id, quantity, Instant.now()));
            }
        }
        if (records.size() != 29) throw new IllegalStateException("Expected 29 legacy inventory items");
        for (var item : records) {
            // Initialize missing IDs only. Existing operational quantities are never reset.
            try {
                mongo.upsert(Query.query(Criteria.where("_id").is(item.itemId())),
                        new Update().setOnInsert("quantity", item.quantity()).setOnInsert("updatedAt", item.updatedAt()), Inventory.class);
            } catch (org.springframework.dao.DuplicateKeyException concurrentSeed) {
                // Another instance initialized the same ID; preserve its quantity.
            }
        }
        log.info("Legacy inventory seed checked itemCount={}", records.size());
    }
}
