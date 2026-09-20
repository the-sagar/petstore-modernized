package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.mdb.petstore.orderprocessing.order.admin.service.AdminStatisticsService;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderLine;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jms.listener.auto-startup=false")
@AutoConfigureMockMvc
class AdminStatisticsIntegrationTests {
    private static final String DATABASE = "petstore_statistics_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired OrderRepository orders;
    @Autowired MongoTemplate mongo;
    @Autowired AdminStatisticsService service;
    @Autowired MockMvc mvc;
    @BeforeEach void reset() { assertEquals(DATABASE, mongo.getDb().getName()); orders.deleteAll(); }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }

    @Test
    void groupsCategoriesAndQuantitiesWithExactDecimalRevenueAndMatchingTotals() throws Exception {
        save("2026-03-01T00:00:00Z", OrderStatus.PENDING,
                line(1, "FISH", 3, "0.10"), line(2, "CATS", 2, "1.234567890123456789"));
        save("2026-03-02T12:00:00Z", OrderStatus.DENIED, line(1, "FISH", 2, "0.20"));
        var result = service.statistics(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-02"));
        assertEquals(List.of("CATS", "FISH"), result.categories().stream().map(c -> c.categoryId()).toList());
        assertEquals(0, new BigDecimal("2.469135780246913578").compareTo(result.categories().getFirst().revenue()));
        assertEquals(2, result.categories().getFirst().unitsSold());
        assertEquals(0, new BigDecimal("0.70").compareTo(result.categories().get(1).revenue()));
        assertEquals(5, result.categories().get(1).unitsSold());
        assertEquals(0, new BigDecimal("3.169135780246913578").compareTo(result.totalRevenue()));
        assertEquals(7, result.totalUnitsSold());
        assertEquals(0, result.totalRevenue().compareTo(result.categories().stream().map(c -> c.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(result.totalUnitsSold(), result.categories().stream().mapToLong(c -> c.unitsSold()).sum());
        var document = mongo.getCollection("orders").find().first();
        assertNotNull(document);
        assertInstanceOf(Decimal128.class, document.getList("lineItems", org.bson.Document.class).getFirst().get("unitPrice"));
        mvc.perform(get("/api/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.endDate").value("2026-03-02"))
                .andExpect(jsonPath("$.totalUnitsSold").value(7))
                .andExpect(jsonPath("$.categories[0].categoryId").value("CATS"));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void everyWorkflowStatusContributesForLegacyParity(OrderStatus status) {
        save("2026-03-01T10:00:00Z", status, line(1, "FISH", 3, "0.10"));
        var result = service.statistics(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-01"));
        assertEquals(3, result.totalUnitsSold());
        assertEquals(0, new BigDecimal("0.30").compareTo(result.totalRevenue()));
    }

    @Test
    void dateRangeIncludesStartAndEntireEndDayButExcludesAdjacentInstants() throws Exception {
        for (String instant : List.of("2026-03-28T23:59:59.999Z", "2026-03-29T00:00:00Z",
                "2026-03-30T23:59:59.999Z", "2026-03-31T00:00:00Z"))
            save(instant, OrderStatus.COMPLETED, line(1, "FISH", 1, "5.00"));
        mvc.perform(get("/api/admin/statistics").param("startDate", "2026-03-29").param("endDate", "2026-03-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalUnitsSold").value(2))
                .andExpect(jsonPath("$.totalRevenue").value(10));
    }

    @Test
    void emptyRangeHasZeroTotalsAndNoCategories() throws Exception {
        save("2025-01-01T00:00:00Z", OrderStatus.APPROVED, line(1, "FISH", 4, "10"));
        mvc.perform(get("/api/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRevenue").value(0))
                .andExpect(jsonPath("$.totalUnitsSold").value(0)).andExpect(jsonPath("$.categories").isEmpty());
    }

    @Test
    void zeroPriceItemsStillCountAsUnits() {
        save("2026-03-01T00:00:00Z", OrderStatus.APPROVED, line(1, "FISH", 4, "0"));
        var result = service.statistics(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-01"));
        assertEquals(0, result.totalRevenue().signum()); assertEquals(4, result.totalUnitsSold());
        assertEquals(1, result.categories().size());
    }

    @Test
    void missingMalformedReversedAndOutOfBoundsDatesAreControlledBadRequests() throws Exception {
        mvc.perform(get("/api/admin/statistics")).andExpect(status().isBadRequest());
        for (String invalid : List.of("", "not-a-date", "2026-02-30", "2026-03-02", "0000-01-01", "+999999999-01-01"))
            mvc.perform(get("/api/admin/statistics").param("startDate", invalid).param("endDate", "2026-03-01"))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/statistics").param("startDate", "2026-03-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/statistics").param("endDate", "2026-03-01")).andExpect(status().isBadRequest());
    }

    private OrderLine line(int number, String category, int quantity, String price) {
        return new OrderLine(number, category, "product", "item-" + number, quantity, new BigDecimal(price));
    }
    private void save(String createdAt, OrderStatus status, OrderLine... lines) {
        // Deliberately unrelated aggregate total: statistics must calculate from line items.
        orders.insert(new Order(UUID.randomUUID().toString(), "customer", "buyer", "test@example.com",
                Instant.parse(createdAt), "en-US", status, null, null, null, List.of(lines), new BigDecimal("999")));
    }
}
