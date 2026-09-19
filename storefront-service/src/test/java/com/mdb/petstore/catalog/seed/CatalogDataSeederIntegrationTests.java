package com.mdb.petstore.catalog.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mongodb.client.MongoClient;
import com.mdb.petstore.catalog.model.CategoryDetails;
import com.mdb.petstore.catalog.model.Item;
import com.mdb.petstore.catalog.model.ItemDetails;
import com.mdb.petstore.catalog.model.Product;
import com.mdb.petstore.catalog.repository.CategoryRepository;
import com.mdb.petstore.catalog.repository.ItemRepository;
import com.mdb.petstore.catalog.repository.ProductRepository;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CatalogDataSeederIntegrationTests {

    private static final String DATABASE = "petstore_catalog_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolateDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }

    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ItemRepository items;
    @Autowired
    private CatalogDataSeeder seeder;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MongoClient mongoClient;

    @AfterAll
    static void cleanUpIsolatedDatabase(@Autowired MongoTemplate mongoTemplate) {
        assertEquals(DATABASE, mongoTemplate.getDb().getName());
        mongoTemplate.getDb().drop();
    }

    @Test
    void seedsExactDocumentAndEmbeddedDetailCounts() {
        assertCounts();
        assertEquals(15, categories.findAll().stream().mapToInt(c -> c.getDetails().size()).sum());
        assertEquals(48, products.findAll().stream().mapToInt(p -> p.getDetails().size()).sum());
        assertEquals(83, items.findAll().stream().mapToInt(i -> i.getDetails().size()).sum());
        assertFalse(mongoTemplate.collectionExists("category_details"));
        assertFalse(mongoTemplate.collectionExists("product_details"));
        assertFalse(mongoTemplate.collectionExists("item_details"));
    }

    @Test
    void preservesFishTranslationsAndAbsentDescription() {
        Map<String, CategoryDetails> details = categories.findById("FISH").orElseThrow().getDetails().stream()
                .collect(Collectors.toMap(CategoryDetails::getLocale, Function.identity()));
        assertEquals(Set.of("en-US", "ja-JP", "zh-CN"), details.keySet());
        assertEquals("Fish", details.get("en-US").getName());
        assertEquals("魚", details.get("ja-JP").getName());
        assertEquals("鱼", details.get("zh-CN").getName());
        details.values().forEach(detail -> {
            assertEquals("fish_icon.gif", detail.getImage());
            assertNull(detail.getDescription());
        });
    }

    @Test
    void preservesLocaleSpecificDecimalPricesAndRepeatedAttributes() {
        Map<String, ItemDetails> details = items.findById("EST-1").orElseThrow().getDetails().stream()
                .collect(Collectors.toMap(ItemDetails::getLocale, Function.identity()));
        assertPrice(details.get("en-US"), "16.50", "10.00");
        assertPrice(details.get("ja-JP"), "1951", "1551");
        assertPrice(details.get("zh-CN"), "142", "86");
        assertEquals(List.of("Large", "Cuddly"), details.get("en-US").getAttributes());
        assertEquals(List.of("大", "優しい"), details.get("ja-JP").getAttributes());
        assertEquals(List.of("大", "喜欢群居"), details.get("zh-CN").getAttributes());
        Document stored = mongoTemplate.getCollection("items").find(new Document("_id", "EST-1")).first();
        assertNotNull(stored);
        assertFalse(stored.containsKey("listPrice"));
        assertFalse(stored.containsKey("unitCost"));
        for (Document detail : stored.getList("details", Document.class)) {
            assertInstanceOf(Decimal128.class, detail.get("listPrice"));
            assertInstanceOf(Decimal128.class, detail.get("unitCost"));
        }
    }

    @Test
    void preservesEst15WithoutSyntheticJapaneseDetails() {
        Item item = items.findById("EST-15").orElseThrow();
        assertEquals("FL-DSH-01", item.getProductId());
        assertEquals(2, item.getDetails().size());
        assertEquals(Set.of("en-US", "zh-CN"), item.getDetails().stream()
                .map(ItemDetails::getLocale).collect(Collectors.toSet()));
    }

    @Test
    void derivesItemCategoriesFromOwningProducts() {
        for (Product product : products.findAll()) {
            assertTrue(categories.existsById(product.getCategoryId()));
        }
        for (Item item : items.findAll()) {
            Product product = products.findById(item.getProductId()).orElseThrow();
            assertEquals(product.getCategoryId(), item.getCategoryId());
        }
        assertEquals("FISH", items.findById("EST-1").orElseThrow().getCategoryId());
        assertEquals("CATS", items.findById("EST-15").orElseThrow().getCategoryId());
    }

    @Test
    void repeatedSeedPreservesCountsAndUserEdits() throws Exception {
        Item item = items.findById("EST-1").orElseThrow();
        BigDecimal original = item.getDetails().getFirst().getListPrice();
        try {
            item.getDetails().getFirst().setListPrice(new BigDecimal("99.99"));
            items.save(item);
            seeder.run(new DefaultApplicationArguments());
            seeder.run(new DefaultApplicationArguments());
            assertCounts();
            assertEquals(new BigDecimal("99.99"), items.findById("EST-1").orElseThrow()
                    .getDetails().getFirst().getListPrice());
        } finally {
            item.getDetails().getFirst().setListPrice(original);
            items.save(item);
        }
    }

    @Test
    void skipsPartialCatalogWithoutFillingOrOverwritingIt() throws Exception {
        String database = DATABASE + "_partial";
        var factory = new SimpleMongoClientDatabaseFactory(mongoClient, database);
        var template = new MongoTemplate(factory);
        try {
            Product existing = new Product();
            existing.setId("existing-product");
            existing.setCategoryId("existing-category");
            template.insert(existing);
            new CatalogDataSeeder(template, new MongoTransactionManager(factory))
                    .run(new DefaultApplicationArguments());
            assertEquals(0, template.getCollection("categories").countDocuments());
            assertEquals(1, template.getCollection("products").countDocuments());
            assertEquals(0, template.getCollection("items").countDocuments());
            assertEquals("existing-category", template.findById("existing-product", Product.class).getCategoryId());
        } finally {
            template.getDb().drop();
        }
    }

    private void assertCounts() {
        assertEquals(5, categories.count());
        assertEquals(16, products.count());
        assertEquals(28, items.count());
    }

    private static void assertPrice(ItemDetails details, String listPrice, String unitCost) {
        assertNotNull(details);
        assertEquals(new BigDecimal(listPrice), details.getListPrice());
        assertEquals(new BigDecimal(unitCost), details.getUnitCost());
    }
}
