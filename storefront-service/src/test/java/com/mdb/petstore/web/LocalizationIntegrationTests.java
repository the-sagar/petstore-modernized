package com.mdb.petstore.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.MessageSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import tools.jackson.databind.ObjectMapper;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LocalizationIntegrationTests {
    private static final String DATABASE = "petstore_i18n_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE + "?replicaSet=rs0");
    }
    @AfterAll
    static void cleanup(@Autowired MongoTemplate mongo) { assertEquals(DATABASE, mongo.getDb().getName()); mongo.getDb().drop(); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MessageSource messages;
    @Autowired CustomerRepository customers;
    @Autowired UserRepository users;

    @ParameterizedTest
    @CsvSource({"en-US,Saved payment method,Replace card number (optional),No saved payment method",
            "ja-JP,保存済みのお支払い方法,カード番号を変更（任意）,保存済みのお支払い方法はありません",
            "zh-CN,已保存的付款方式,更换卡号（可选）,没有已保存的付款方式"})
    void paymentPresentationIsLocalizedAndReplacementInputIsEmpty(String locale, String saved, String replace, String empty) throws Exception {
        String html = mvc.perform(get("/account").param("locale", locale).with(user("buyer")))
                .andExpect(status().isOk()).andExpect(content().string(containsString(saved)))
                .andExpect(content().string(containsString(replace)))
                .andExpect(content().string(containsString(empty))).andReturn().getResponse().getContentAsString();
        var input = Pattern.compile("<input[^>]*name=\"cardNumber\"[^>]*>").matcher(html);
        assertTrue(input.find());
        assertTrue(input.group().contains("type=\"password\""));
        assertTrue(input.group().contains("autocomplete=\"off\""));
        assertTrue(input.group().contains("inputmode=\"numeric\""));
        assertFalse(input.group().contains("value="));
        assertTrue(html.contains("id=\"saved-card-number\" class=\"saved-card-number\" aria-hidden=\"true\""));
    }

    @ParameterizedTest
    @CsvSource({"en-US,Find your next companion", "ja_JP,新しい仲間を見つけよう", "zh-CN,寻找您的新伙伴"})
    void rendersLocalizedPagesAndJavascriptDictionary(String input, String heading) throws Exception {
        String tag = input.replace('_', '-');
        String html = mvc.perform(get("/shop").param("locale", input)).andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"" + tag + "\"")))
                .andExpect(content().string(containsString(heading))).andReturn().getResponse().getContentAsString();
        var selector = Pattern.compile("<select id=\"locale-select\".*?</select>", Pattern.DOTALL).matcher(html);
        assertTrue(selector.find());
        var values = Pattern.compile("value=\"([^\"]+)\"").matcher(selector.group());
        assertEquals(List.of("en-US", "ja-JP", "zh-CN"), values.results().map(m -> m.group(1)).toList());
        var dictionary = Pattern.compile("window.petstoreMessages = (\\{.*?\\});", Pattern.DOTALL).matcher(html);
        assertTrue(dictionary.find());
        var json = mapper.readTree(dictionary.group(1));
        for (String key : List.of("js.searching", "js.cartUpdated", "js.validation", "js.unavailable", "js.page")) {
            assertEquals(messages.getMessage(key, null, Locale.forLanguageTag(tag)), json.get(key).asString());
        }
        for (String path : List.of("/shop/categories/FISH", "/shop/products/FI-SW-01", "/cart", "/checkout", "/login", "/register", "/account")) {
            mvc.perform(get(path).param("locale", input).with(user("render").roles("CUSTOMER")))
                    .andExpect(status().isOk()).andExpect(content().string(not(matchesPattern("(?s).*\\?\\?[a-z]+\\.[A-Za-z].*"))))
                    .andExpect(content().string(containsString("lang=\"" + tag + "\"")));
        }
    }

    @Test
    void explicitLocaleUpdatesAnonymousSessionAndUnsupportedValuesFallBack() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/shop").session(session).param("locale", "zh_CN"))
                .andExpect(content().string(containsString("寻找您的新伙伴")));
        assertEquals(Locale.SIMPLIFIED_CHINESE, session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME));
        mvc.perform(get("/shop").session(session)).andExpect(content().string(containsString("寻找您的新伙伴")));
        for (String invalid : List.of("fr-FR", "", "../../messages")) {
            mvc.perform(get("/shop").session(session).param("locale", invalid))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Find your next companion")));
        }
    }

    @ParameterizedTest
    @CsvSource({"en_US,en-US", "ja-JP,ja-JP", "zh_CN,zh-CN", "fr-FR,en-US", "' ',en-US"})
    void registrationNormalizesPreferenceAndAccountReturnsIt(String input, String expected) throws Exception {
        var data = registration(input);
        register(data, new MockHttpSession());
        var session = login(data.get("username"), "test-password");
        mvc.perform(get("/api/account").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.languagePreference").value(expected));
        assertEquals(Locale.forLanguageTag(expected), session.getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME));
    }

    @Test
    void omittedRegistrationPreferenceUsesEffectiveLocale() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/register").session(session).param("locale", "ja-JP")).andExpect(status().isOk());
        var data = registration(null);
        register(data, session);
        var customer = customers.findById(users.findByUsername(data.get("username")).orElseThrow().getCustomerId()).orElseThrow();
        assertEquals("ja-JP", customer.getProfile().getLanguagePreference());
    }

    @Test
    void preferenceAppliesOnLoginButExplicitDisplayChoiceDoesNotSaveIt() throws Exception {
        var data = registration("ja-JP"); register(data, new MockHttpSession());
        var session = login(data.get("username"), "test-password");
        mvc.perform(get("/shop").session(session)).andExpect(content().string(containsString("新しい仲間を見つけよう")));
        mvc.perform(get("/shop").session(session).param("locale", "zh-CN"))
                .andExpect(content().string(containsString("寻找您的新伙伴")));
        mvc.perform(get("/api/account").session(session)).andExpect(jsonPath("$.languagePreference").value("ja-JP"));
        // Without an explicit query, persisted preference takes precedence over the session fallback.
        mvc.perform(get("/shop").session(session)).andExpect(content().string(containsString("新しい仲間を見つけよう")));
        data.put("languagePreference", "zh_CN");
        mvc.perform(put("/api/account").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(data)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.languagePreference").value("zh-CN"));
        mvc.perform(get("/api/account").session(session)).andExpect(jsonPath("$.languagePreference").value("zh-CN"));
        var nextLogin = login(data.get("username"), "test-password");
        mvc.perform(get("/shop").session(nextLogin)).andExpect(content().string(containsString("寻找您的新伙伴")));
    }

    @ParameterizedTest
    @CsvSource({"admin,/admin/orders", "supplier,/supplier/inventory"})
    void operationalLoginNeedsNoCustomerAndConsoleStaysEnglish(String username, String path) throws Exception {
        long before = customers.count();
        var session = login(username, username);
        assertNull(users.findByUsername(username).orElseThrow().getCustomerId());
        assertEquals(before, customers.count());
        mvc.perform(get(path).session(session).param("locale", "ja-JP"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("id=\"locale-select\""))))
                .andExpect(content().string(containsString("lang=\"en\"")));
    }

    private Map<String, String> registration(String preference) {
        Map<String, String> data = new LinkedHashMap<>(Map.of("username", "locale." + UUID.randomUUID(),
                "password", "test-password", "firstName", "Local", "lastName", "Tester", "email", "test@example.com",
                "street1", "Demo Street", "city", "Demo", "postalCode", "123", "country", "US"));
        if (preference != null) data.put("languagePreference", preference);
        return data;
    }
    private void register(Map<String, String> data, MockHttpSession session) throws Exception {
        mvc.perform(post("/api/auth/register").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(data))).andExpect(status().isCreated());
    }
    private MockHttpSession login(String username, String password) throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).param("username", username).param("password", password))
                .andExpect(status().isOk());
        return session;
    }
}
