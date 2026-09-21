package com.mdb.petstore.web;

import java.util.UUID;
import java.util.regex.Pattern;

import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.service.RegistrationService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StorefrontPageIntegrationTests {

    private static final String DATABASE = "petstore_pages_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }

    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) {
        assertEquals(DATABASE, mongo.getDb().getName());
        mongo.getDb().drop();
    }

    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"en-US,Item detail,Price", "ja-JP,商品詳細,価格", "zh-CN,商品详情,价格"})
    void itemDetailPageIsPublicAndLocalized(String locale, String title, String price) throws Exception {
        mvc.perform(get("/shop/items/EST-1").param("locale", locale))
                .andExpect(status().isOk()).andExpect(view().name("item"))
                .andExpect(content().string(containsString("data-page=\"item\"")))
                .andExpect(content().string(containsString("lang=\"" + locale + "\"")))
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString(price)))
                .andExpect(content().string(containsString("id=\"item-content\" hidden")))
                .andExpect(content().string(containsString("href=\"/shop\" data-locale-link")))
                .andExpect(content().string(containsString("href=\"/cart\" data-locale-link")));
        mvc.perform(get("/api/catalog/items/MISSING").param("locale", locale))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyArtworkIsNotServedButImageMetadataIsRetained() throws Exception {
        var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath*:/static/assets/images/catalog/*");
        assertEquals(0, resources.length);
        var catalog = new org.springframework.core.io.ClassPathResource("legacy/catalog.xml");
        String xml = new String(catalog.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        var filenames = Pattern.compile("<Image>([^<]+)</Image>").matcher(xml).results()
                .map(match -> match.group(1)).distinct().toList();
        assertEquals(20, filenames.size());
        assertTrue(filenames.contains("fish1.jpg"));
        for (String filename : filenames) {
            mvc.perform(get("/assets/images/catalog/" + filename)).andExpect(status().isNotFound());
        }
    }

    @Test
    void catalogPagesExposeAccessibleInitiallyDisabledPagination() throws Exception {
        for (String path : new String[] {"/shop", "/shop/categories/FISH", "/shop/products/FI-SW-01"}) {
            mvc.perform(get(path).param("locale", "ja-JP").param("page", "1").param("q", "fish"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("aria-label=\"カタログのページ切替\"")))
                    .andExpect(content().string(containsString("id=\"catalog-previous\" type=\"button\" disabled")))
                    .andExpect(content().string(containsString("id=\"catalog-next\" type=\"button\" disabled")))
                    .andExpect(content().string(containsString("id=\"catalog-page\" role=\"status\"")));
        }
    }

    @Test
    void shopIsPublicWithCategoriesSearchAndNavigation() throws Exception {
        mvc.perform(get("/shop").param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(view().name("shop"))
                .andExpect(content().string(containsString("id=\"categories\"")))
                .andExpect(content().string(containsString("id=\"search-form\"")))
                .andExpect(content().string(containsString("href=\"/cart\"")))
                .andExpect(content().string(containsString("href=\"/account\"")))
                .andExpect(content().string(containsString("id=\"locale-select\"")));
    }

    @Test
    void categoryPageIsPublic() throws Exception {
        mvc.perform(get("/shop/categories/FISH"))
                .andExpect(status().isOk()).andExpect(view().name("category"))
                .andExpect(content().string(containsString("id=\"products\"")));
    }

    @Test
    void productPageIsPublicWithItemContainerAndCsrfMetadata() throws Exception {
        mvc.perform(get("/shop/products/FI-SW-01"))
                .andExpect(status().isOk()).andExpect(view().name("product"))
                .andExpect(content().string(containsString("id=\"items\"")))
                .andExpect(content().string(matchesPattern("(?s).*name=\"_csrf\" content=\"[^\"]+\".*")))
                .andExpect(content().string(containsString("name=\"_csrf_header\" content=\"X-CSRF-TOKEN\"")));
    }

    @Test
    void cartPageIsPublicWithCheckoutLinkAndCsrfMetadata() throws Exception {
        mvc.perform(get("/cart"))
                .andExpect(status().isOk()).andExpect(view().name("cart"))
                .andExpect(content().string(containsString("id=\"cart-lines\"")))
                .andExpect(content().string(containsString("href=\"/checkout\"")))
                .andExpect(content().string(containsString("Continue shopping")))
                .andExpect(content().string(matchesPattern("(?s).*name=\"_csrf\" content=\"[^\"]+\".*")));
    }

    @Test
    void checkoutPageRequiresAuthentication() throws Exception {
        mvc.perform(get("/checkout"))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location", endsWith("/login")));
        mvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void authenticatedCheckoutRendersContactsConfirmationAndCsrfWithoutPaymentInputs() throws Exception {
        mvc.perform(get("/checkout").with(user("page-user").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(view().name("checkout"))
                .andExpect(content().string(containsString("name=\"billing.firstName\"")))
                .andExpect(content().string(containsString("name=\"shipping.street1\"")))
                .andExpect(content().string(containsString("id=\"same-as-billing\"")))
                .andExpect(content().string(containsString("id=\"order-confirmation\"")))
                .andExpect(content().string(matchesPattern("(?s).*name=\"_csrf\" content=\"[^\"]+\".*")))
                .andExpect(content().string(not(containsString("name=\"cardNumber\""))))
                .andExpect(content().string(not(containsString("localhost:8081"))));
    }

    @Test
    void catalogRemainsPublicAndCartWritesStillRequireCsrf() throws Exception {
        mvc.perform(get("/api/catalog/categories")).andExpect(status().isOk());
        mvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"EST-1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void scriptAndExistingAuthNavigationArePublic() throws Exception {
        mvc.perform(get("/assets/storefront.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("t('js.unavailable')")));
        for (String path : new String[] {"/login", "/register"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/shop\"")))
                    .andExpect(content().string(containsString("href=\"/cart\"")));
        }
        mvc.perform(get("/account").with(user("page-user").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/shop\"")))
                .andExpect(content().string(containsString("href=\"/cart\"")));
    }

    @Test
    void renderedCsrfTokenWorksAndAnonymousCartSurvivesRealLogin() throws Exception {
        var session = new MockHttpSession();
        var page = mvc.perform(get("/shop/products/FI-SW-01").session(session))
                .andExpect(status().isOk()).andReturn();
        var token = Pattern.compile("name=\"_csrf\" content=\"([^\"]+)\"")
                .matcher(page.getResponse().getContentAsString());
        assertTrue(token.find());
        mvc.perform(post("/api/cart/items").session(session).header("X-CSRF-TOKEN", token.group(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"EST-1\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/checkout").session(session)).andExpect(status().is3xxRedirection());

        var request = new RegisterRequest();
        request.setUsername("page-handoff-" + UUID.randomUUID());
        request.setPassword("Page-test-password-123!");
        request.setFirstName("Page"); request.setLastName("Customer");
        request.setEmail(request.getUsername() + "@example.com");
        request.setStreet1("1 Test Street"); request.setCity("Dublin");
        request.setPostalCode("D01"); request.setCountry("IE");
        registration.register(request);
        var login = mvc.perform(post("/api/auth/login").session(session)
                        .param("username", request.getUsername()).param("password", request.getPassword()))
                .andExpect(status().isOk()).andReturn();
        var loggedInSession = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(loggedInSession);
        mvc.perform(get("/checkout").session(loggedInSession)).andExpect(status().isOk());
        mvc.perform(get("/api/cart").session(loggedInSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1)).andExpect(jsonPath("$.items[0].itemId").value("EST-1"));
    }
}
