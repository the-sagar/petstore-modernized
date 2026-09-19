package com.mdb.petstore.catalog.seed;

import com.mdb.petstore.catalog.model.Category;
import com.mdb.petstore.catalog.model.Item;
import com.mdb.petstore.catalog.model.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CatalogDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogDataSeeder.class);

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;

    public CatalogDataSeeder(MongoTemplate mongoTemplate, MongoTransactionManager transactionManager) {
        this.mongoTemplate = mongoTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (catalogExists()) {
            log.info("Catalog seed skipped because catalog already exists");
            return;
        }
        log.info("Catalog seed started");
        LegacyCatalogParser.CatalogData catalog;
        try (var input = new ClassPathResource("legacy/catalog.xml").getInputStream()) {
            catalog = new LegacyCatalogParser().parse(input);
        }

        // Create empty collections before the transaction; never drop or replace existing collections.
        for (Class<?> type : new Class<?>[] {Category.class, Product.class, Item.class}) {
            if (!mongoTemplate.collectionExists(type)) {
                mongoTemplate.createCollection(type);
            }
        }
        Boolean inserted = transactionTemplate.execute(status -> {
            // Recheck after parsing. Any existing catalog data is preserved, even a partial catalog.
            if (catalogExists()) {
                return false;
            }
            // Inserts, not saves/upserts: an existing ID can never be overwritten.
            mongoTemplate.insert(catalog.categories(), Category.class);
            mongoTemplate.insert(catalog.products(), Product.class);
            mongoTemplate.insert(catalog.items(), Item.class);
            return true;
        });
        if (Boolean.TRUE.equals(inserted)) {
            log.info("Catalog seed completed: categories={}, products={}, items={}",
                    catalog.categories().size(), catalog.products().size(), catalog.items().size());
        } else {
            log.info("Catalog seed skipped because catalog already exists");
        }
    }

    private boolean catalogExists() {
        return mongoTemplate.exists(new Query(), Category.class)
                || mongoTemplate.exists(new Query(), Product.class)
                || mongoTemplate.exists(new Query(), Item.class);
    }
}
