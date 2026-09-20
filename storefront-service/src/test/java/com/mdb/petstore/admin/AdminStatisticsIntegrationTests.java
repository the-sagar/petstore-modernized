package com.mdb.petstore.admin;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"petstore.admin.bootstrap.username=", "petstore.admin.bootstrap.password="})
@AutoConfigureMockMvc
@Import(AdminIntegrationTests.HttpStub.class)
class AdminStatisticsIntegrationTests {
    private static final String DATABASE = "petstore_statistics_ui_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DATA = """
            {"startDate":"2026-03-01","endDate":"2026-03-02","totalRevenue":0.70,"totalUnitsSold":5,
            "categories":[{"categoryId":"CATS","revenue":0.40,"unitsSold":2},
            {"categoryId":"FISH","revenue":0.30,"unitsSold":3}]}
            """;
    private static final String URL = "http://order.test/api/admin/statistics?startDate=2026-03-01&endDate=2026-03-02";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("adminServer") MockRestServiceServer server;
    @Autowired MongoTemplate mongo;
    @BeforeEach void reset() { assertEquals(DATABASE, mongo.getDb().getName()); server.reset(); }
    @AfterEach void verifyCalls() { server.verify(); }
    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop();
    }

    @Test
    void adminPageRendersExactValuesPercentagesAndNavigation() throws Exception {
        expect(DATA);
        mvc.perform(get("/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin-statistics"))
                .andExpect(content().string(containsString("Admin Statistics")))
                .andExpect(content().string(containsString("Revenue by Category")))
                .andExpect(content().string(containsString("Units Sold by Category")))
                .andExpect(content().string(containsString("0.70")))
                .andExpect(content().string(containsString("class=\"revenue-donut\"")))
                .andExpect(content().string(containsString("stroke-dasharray=\"0.40 0.70\"")))
                .andExpect(content().string(containsString("stroke-dasharray=\"0.30 0.70\"")))
                .andExpect(content().string(containsString("stroke-dashoffset=\"-0.40\"")))
                .andExpect(content().string(containsString("Revenue by category legend")))
                .andExpect(content().string(containsString("CATS share of units sold")))
                .andExpect(content().string(containsString("FISH share of units sold")))
                .andExpect(content().string(not(containsString("CATS revenue percentage"))))
                .andExpect(content().string(containsString("57.14")))
                .andExpect(content().string(containsString("42.86")))
                .andExpect(content().string(containsString("all order statuses")))
                .andExpect(content().string(containsString("UTC")))
                .andExpect(content().string(containsString("href=\"/admin/orders\"")))
                .andExpect(content().string(not(containsString(":8081"))));
        mvc.perform(get("/admin/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/admin/statistics\"")));
    }

    @Test
    void adminProxyReturnsTypedStatistics() throws Exception {
        expect(DATA);
        mvc.perform(get("/api/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRevenue").value(0.70))
                .andExpect(jsonPath("$.totalUnitsSold").value(5)).andExpect(jsonPath("$.categories[0].categoryId").value("CATS"));
    }

    @Test
    void customerAndSupplierCannotReadPageOrProxy() throws Exception {
        for (String role : List.of("CUSTOMER", "SUPPLIER"))
            for (String path : List.of("/admin/statistics", "/api/admin/statistics"))
                mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequiresLoginForPageAndAuthenticationForProxy() throws Exception {
        mvc.perform(get("/admin/statistics")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
        mvc.perform(get("/api/admin/statistics")).andExpect(status().isUnauthorized());
    }

    @Test
    void defaultRangeIsThirtyInclusiveUtcCalendarDays() throws Exception {
        var today = LocalDate.now(ZoneOffset.UTC);
        var start = today.minusDays(29);
        server.expect(requestTo("http://order.test/api/admin/statistics?startDate=" + start + "&endDate=" + today))
                .andRespond(withSuccess("{\"startDate\":\"" + start + "\",\"endDate\":\"" + today
                        + "\",\"totalRevenue\":0,\"totalUnitsSold\":0,\"categories\":[]}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/admin/statistics").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(model().attribute("startDate", start.toString())).andExpect(model().attribute("endDate", today.toString()))
                .andExpect(content().string(containsString("No sales data for the selected period.")))
                .andExpect(content().string(not(containsString("class=\"revenue-donut\""))));
    }

    @Test
    void zeroRevenueWithUnitsProducesZeroPercentages() throws Exception {
        expect(DATA.replace("0.70", "0").replace("0.40", "0").replace("0.30", "0"));
        mvc.perform(get("/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02")
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("NaN"))))
                .andExpect(content().string(containsString("No revenue for the selected period.")))
                .andExpect(content().string(not(containsString("stroke-dasharray"))))
                .andExpect(content().string(containsString("CATS share of units sold")))
                .andExpect(content().string(not(containsString("No sales data"))));
    }

    @Test
    void singleRevenueCategoryRendersAFullDonutAndExactLegend() throws Exception {
        expect("""
                {"startDate":"2026-03-01","endDate":"2026-03-02","totalRevenue":1.23456789,"totalUnitsSold":3,
                "categories":[{"categoryId":"FISH","revenue":1.23456789,"unitsSold":3}]}
                """);
        mvc.perform(get("/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02")
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("stroke-dasharray=\"1.23456789 1.23456789\"")))
                .andExpect(content().string(containsString("stroke-dashoffset=\"0\"")))
                .andExpect(content().string(containsString("100.00")))
                .andExpect(content().string(containsString(">1.23456789</span>")))
                .andExpect(content().string(containsString(">3</span> units")));
    }

    @Test
    void invalidDatesRenderControlledPageOrApiBadRequestWithoutCallingBackend() throws Exception {
        for (String path : List.of("/admin/statistics", "/api/admin/statistics")) {
            for (String value : List.of("", "invalid", "2026-02-30", "2026-03-03"))
                mvc.perform(get(path).param("startDate", value).param("endDate", "2026-03-02")
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("startDate", "2026-03-01").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/statistics").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void backendFailureRendersSafeErrorPage() throws Exception {
        server.expect(requestTo(URL)).andRespond(withServerError().body("private downstream detail"));
        mvc.perform(get("/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02")
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("Statistics are temporarily unavailable")))
                .andExpect(content().string(not(containsString("private downstream"))));
    }

    @Test
    void malformedOrInconsistentBackendDataIsNotPresentedAsStatistics() throws Exception {
        for (String body : List.of("{}", "{", DATA.replace("0.70", "0.80"), DATA.replace("2026-03-01", "2025-03-01"))) {
            server.reset(); expect(body);
            mvc.perform(get("/api/admin/statistics").param("startDate", "2026-03-01").param("endDate", "2026-03-02")
                    .with(user("admin").roles("ADMIN"))).andExpect(status().isBadGateway());
            server.verify();
        }
    }

    private void expect(String body) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
