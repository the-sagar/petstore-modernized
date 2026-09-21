package com.mdb.petstore.customer.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;
import com.mdb.petstore.identity.service.RegistrationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTests {

    private final List<User> testUsers = new ArrayList<>();
    private final String password = "Account-integration-password-123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUpTestData() {
        for (User user : testUsers) {
            userRepository.deleteById(user.getId());
            customerRepository.deleteById(user.getCustomerId());
        }
    }

    @Test
    void unauthenticatedGetReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void retrievesOwnAccountWithoutSensitiveFields() throws Exception {
        User user = registerCustomer();
        mockMvc.perform(get("/api/account").session(login(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(16)))
                .andExpect(jsonPath("$.firstName").value("Original"))
                .andExpect(jsonPath("$.lastName").value("Customer"))
                .andExpect(jsonPath("$.email").value(user.getUsername() + "@example.com"))
                .andExpect(jsonPath("$.city").value("Dublin"))
                .andExpect(jsonPath("$.street1").value("1 Original Street"))
                .andExpect(jsonPath("$.cardType").value("VISA"))
                .andExpect(jsonPath("$.expiryDate").value("12/2030"))
                .andExpect(jsonPath("$.languagePreference").value("en-US"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.cardNumber").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void updatesEmbeddedValuesAndPreservesCreationTimeAndUser() throws Exception {
        User user = registerCustomer();
        Customer before = customer(user);
        // A deterministic prior timestamp avoids relying on clock resolution or sleeps.
        before.setUpdatedAt(Instant.parse("2000-01-01T00:00:00Z"));
        customerRepository.save(before);
        String userBefore = objectMapper.writeValueAsString(userRepository.findById(user.getId()).orElseThrow());
        Map<String, Object> update = updateRequest(user);

        mockMvc.perform(put("/api/account").session(login(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.city").value("Cork"))
                .andExpect(jsonPath("$.cardNumber").doesNotExist());

        Customer after = customer(user);
        assertEquals(before.getCreatedAt(), after.getCreatedAt());
        assertTrue(after.getUpdatedAt().isAfter(before.getUpdatedAt()));
        assertEquals(userBefore, objectMapper.writeValueAsString(userRepository.findById(user.getId()).orElseThrow()));
        Map<String, Object> stored = new LinkedHashMap<>();
        var contact = after.getAccount().getContactInfo();
        stored.put("firstName", contact.getFirstName());
        stored.put("lastName", contact.getLastName());
        stored.put("email", contact.getEmail());
        stored.put("phone", contact.getPhone());
        var address = contact.getAddress();
        stored.put("street1", address.getStreet1());
        stored.put("street2", address.getStreet2());
        stored.put("city", address.getCity());
        stored.put("stateOrProvince", address.getStateOrProvince());
        stored.put("postalCode", address.getPostalCode());
        stored.put("country", address.getCountry());
        var card = after.getAccount().getCreditCard();
        stored.put("cardType", card.getCardType());
        stored.put("last4", card.getLast4());
        stored.put("expiryDate", card.getExpiryDate());
        var profile = after.getProfile();
        stored.put("languagePreference", profile.getLanguagePreference());
        stored.put("bannerPreference", profile.isBannerPreference());
        stored.put("linkPreference", profile.isLinkPreference());
        update.remove("cardNumber");
        update.put("last4", "4444");
        update.put("languagePreference", "en-US"); // Unsupported saved preferences normalize to English.
        assertEquals(update, stored);
    }

    @Test
    void invalidUpdateLeavesAccountUnchanged() throws Exception {
        User user = registerCustomer();
        String before = objectMapper.writeValueAsString(customer(user));
        Map<String, Object> request = updateRequest(user);
        request.put("email", "invalid-email");
        mockMvc.perform(put("/api/account").session(login(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertEquals(before, objectMapper.writeValueAsString(customer(user)));
    }

    @Test
    void updateRequiresCsrfToken() throws Exception {
        User user = registerCustomer();
        String before = objectMapper.writeValueAsString(customer(user));
        mockMvc.perform(put("/api/account").session(login(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest(user))))
                .andExpect(status().isForbidden());
        assertEquals(before, objectMapper.writeValueAsString(customer(user)));
    }

    @Test
    void clientSuppliedIdentityCannotSelectAnotherCustomer() throws Exception {
        User userA = registerCustomer();
        User userB = registerCustomer();
        MockHttpSession session = login(userA);
        String customerBBefore = objectMapper.writeValueAsString(customer(userB));
        String userABefore = objectMapper.writeValueAsString(userRepository.findById(userA.getId()).orElseThrow());

        mockMvc.perform(get("/api/account").session(session)
                        .param("customerId", userB.getCustomerId()).param("username", userB.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userA.getUsername() + "@example.com"));

        Map<String, Object> request = updateRequest(userA);
        request.put("customerId", userB.getCustomerId());
        request.put("username", userB.getUsername());
        request.put("roles", List.of("ADMIN"));
        request.put("enabled", false);
        request.put("password", "Attempted-password-change");
        var result = mockMvc.perform(put("/api/account").session(session).with(csrf())
                        .param("customerId", userB.getCustomerId())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andReturn();
        // Either rejecting unknown fields or ignoring them is safe; they cannot select ownership.
        assertTrue(result.getResponse().getStatus() == 200 || result.getResponse().getStatus() == 400);
        if (result.getResponse().getStatus() == 400) {
            mockMvc.perform(put("/api/account").session(session).with(csrf())
                            .param("customerId", userB.getCustomerId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest(userA))))
                    .andExpect(status().isOk());
        }
        assertEquals("Updated", customer(userA).getAccount().getContactInfo().getFirstName());
        assertEquals(customerBBefore, objectMapper.writeValueAsString(customer(userB)));
        assertEquals(userABefore, objectMapper.writeValueAsString(userRepository.findById(userA.getId()).orElseThrow()));
    }

    private Customer customer(User user) {
        return customerRepository.findById(user.getCustomerId()).orElseThrow();
    }

    private MockHttpSession login(User user) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .param("username", user.getUsername()).param("password", password))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private User registerCustomer() {
        String username = "account.it." + UUID.randomUUID();
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setFirstName("Original");
        request.setLastName("Customer");
        request.setEmail(username + "@example.com");
        request.setStreet1("1 Original Street");
        request.setCity("Dublin");
        request.setPostalCode("D02 TEST");
        request.setCountry("Ireland");
        request.setCardType("VISA");
        request.setCardNumber("4111111111111111");
        request.setExpiryDate("12/2030");
        request.setLanguagePreference("en");
        User user = registrationService.register(request);
        testUsers.add(user);
        return user;
    }

    private Map<String, Object> updateRequest(User user) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("firstName", "Updated");
        request.put("lastName", "Account");
        request.put("email", "updated." + user.getUsername() + "@example.com");
        request.put("phone", "555-0101");
        request.put("street1", "2 Updated Street");
        request.put("street2", "Unit 3");
        request.put("city", "Cork");
        request.put("stateOrProvince", "Cork");
        request.put("postalCode", "T12 TEST");
        request.put("country", "Ireland");
        request.put("cardType", "MASTERCARD");
        request.put("cardNumber", "5555555555554444");
        request.put("expiryDate", "11/2031");
        request.put("languagePreference", "fr");
        request.put("bannerPreference", true);
        request.put("linkPreference", true);
        return request;
    }
}
