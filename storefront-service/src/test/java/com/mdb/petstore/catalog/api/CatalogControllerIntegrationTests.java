package com.mdb.petstore.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.mdb.petstore.catalog.dto.ItemResponse;
import com.mdb.petstore.catalog.repository.CategoryRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerIntegrationTests {

    private static final String DATABASE = "petstore_catalog_api_test_" + UUID.randomUUID().toString().replace("-", "");

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
    private CategoryRepository categories;

    @Test
    void listsFiveEnglishCategoriesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Fish", "Dogs", "Reptiles", "Cats", "Birds")))
                .andExpect(jsonPath("$[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$[0].details").doesNotExist());
    }

    @Test
    void resolvesFishLocalesAndNormalizesLanguageTags() throws Exception {
        mockMvc.perform(get("/api/catalog/categories/FISH").param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("魚"));
        mockMvc.perform(get("/api/catalog/categories/FISH").param("locale", " zh_cn "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("鱼"));
        mockMvc.perform(get("/api/catalog/categories/FISH").param("locale", "EN-us"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fish"));
    }

    @Test
    void listsTheFourLegacyFishProducts() throws Exception {
        mockMvc.perform(get("/api/catalog/categories/FISH/products").param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder("FI-SW-01", "FI-SW-02", "FI-FW-01", "FI-FW-02")))
                .andExpect(jsonPath("$[*].categoryId", everyItem(is("FISH"))))
                .andExpect(jsonPath("$[*].name", hasItem("エンゼルフィッシュ")));
    }

    @Test
    void looksUpLocalizedProductsAndTheirItems() throws Exception {
        mockMvc.perform(get("/api/catalog/products/FI-SW-01").param("locale", "zh-CN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("FI-SW-01"))
                .andExpect(jsonPath("$.name").value("天使鱼"))
                .andExpect(jsonPath("$", aMapWithSize(5))).andExpect(jsonPath("$.details").doesNotExist());
        mockMvc.perform(get("/api/catalog/products/FI-SW-01/items").param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", containsInAnyOrder("EST-1", "EST-2")))
                .andExpect(jsonPath("$[*].productId", everyItem(is("FI-SW-01"))))
                .andExpect(jsonPath("$[0].description").value("日本産の淡水魚"));
    }

    @Test
    void returnsLocaleSpecificBigDecimalItemPrices() throws Exception {
        ItemResponse english = item("EST-1", "en-US");
        assertEquals(new BigDecimal("16.50"), english.listPrice());
        assertEquals(new BigDecimal("10.00"), english.unitCost());
        assertEquals("FISH", english.categoryId());
        assertEquals("FI-SW-01", english.productId());
        ItemResponse japanese = item("EST-1", "ja-JP");
        assertEquals(new BigDecimal("1951"), japanese.listPrice());
        assertEquals(new BigDecimal("1551"), japanese.unitCost());
        assertEquals("日本産の淡水魚", japanese.description());
        ItemResponse chinese = item("EST-1", "zh-CN");
        assertEquals(new BigDecimal("142"), chinese.listPrice());
        assertEquals(new BigDecimal("86"), chinese.unitCost());
        assertEquals("日本产的淡水鱼", chinese.description());
    }

    @Test
    void est15FallsBackToItsRealEnglishDetails() throws Exception {
        ItemResponse english = item("EST-15", "en-US");
        assertEquals(english, item("EST-15", "ja-JP"));
        assertEquals(new BigDecimal("23.50"), english.listPrice());
        assertEquals("Great for reducing mouse populations", english.description());
        assertEquals(english, item("EST-15", "fr-FR"));
        assertEquals(english, item("EST-15", " "));
    }

    @Test
    void fallsBackToFirstDetailWhenRequestedAndEnglishAreAbsent() throws Exception {
        var category = categories.findById("FISH").orElseThrow();
        var original = category.getDetails();
        try {
            category.setDetails(original.stream().filter(detail -> !detail.getLocale().equals("en-US")).toList());
            categories.save(category);
            mockMvc.perform(get("/api/catalog/categories/FISH").param("locale", "fr-FR"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("魚"));
        } finally {
            category.setDetails(original);
            categories.save(category);
        }
    }

    @Test
    void searchMatchesNamesCaseInsensitivelyAndRequiresEveryToken() throws Exception {
        for (String query : new String[] {"Angelfish", "aNgElFiSh", "  ANGELFISH\tAustralia\nFISH  "}) {
            mockMvc.perform(get("/api/catalog/search").param("q", query))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains("FI-SW-01")));
        }
        mockMvc.perform(get("/api/catalog/search").param("q", "Angelfish nonexistent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void searchIncludesProductDescriptionsCategoriesAndAssociatedItemDescriptions() throws Exception {
        mockMvc.perform(get("/api/catalog/search").param("q", "fish australia"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", containsInAnyOrder("FI-SW-01", "FI-SW-02")));
        mockMvc.perform(get("/api/catalog/search").param("q", "reptiles"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].categoryId", everyItem(is("REPTILES"))))
                .andExpect(jsonPath("$", hasSize(2)));
        // Angelfish's product description says Australia; Japan comes only from its items.
        mockMvc.perform(get("/api/catalog/search").param("q", "Angelfish Japan"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains("FI-SW-01")));
    }

    @Test
    void searchUsesRequestedLocalesAndPerDocumentFallback() throws Exception {
        mockMvc.perform(get("/api/catalog/search").param("q", "天使鱼").param("locale", "zh-CN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains("FI-SW-01")))
                .andExpect(jsonPath("$[0].name").value("天使鱼"));
        mockMvc.perform(get("/api/catalog/search").param("q", "エンゼルフィッシュ").param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains("FI-SW-01")));
        // EST-15's English fallback contributes to a Japanese search for its owning product.
        mockMvc.perform(get("/api/catalog/search").param("q", "マンクスネコ mouse").param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains("FL-DSH-01")));
    }

    @Test
    void blankOrMissingSearchReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/catalog/search"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/catalog/search").param("q", " \t\n "))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void unknownIdsAndUnknownParentsReturn404() throws Exception {
        for (String path : new String[] {"categories/missing", "products/missing", "items/missing",
                "categories/missing/products", "products/missing/items"}) {
            mockMvc.perform(get("/api/catalog/" + path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void publicCatalogAccessDoesNotPermitWritesOrAccountAccess() throws Exception {
        mockMvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/catalog/categories").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    private ItemResponse item(String id, String locale) throws Exception {
        var result = mockMvc.perform(get("/api/catalog/items/" + id).param("locale", locale))
                .andExpect(status().isOk()).andExpect(jsonPath("$", aMapWithSize(8)))
                .andExpect(jsonPath("$.details").doesNotExist()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), ItemResponse.class);
    }
}
