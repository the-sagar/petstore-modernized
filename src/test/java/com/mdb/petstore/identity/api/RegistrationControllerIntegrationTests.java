package com.mdb.petstore.identity.api;

import java.util.Locale;
import java.util.UUID;

import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerIntegrationTests {

    private final String username = "reg.http." + UUID.randomUUID();
    private final String email = username + "@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUpTestData() {
        mongoTemplate.remove(Query.query(where("username").is(username)), User.class);
        mongoTemplate.remove(Query.query(where("account.contactInfo.email").is(email)), Customer.class);
    }

    @Test
    void registersCustomerAndReturnsOnlyPublicIdentityFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("  " + username.toUpperCase(Locale.ROOT) + "  ", "Customer")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.id", not(emptyOrNullString())))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.customerId", not(emptyOrNullString())))
                .andExpect(jsonPath("$.roles", contains("CUSTOMER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.enabled").doesNotExist());

        User user = userRepository.findByUsername(username).orElseThrow();
        assertEquals(1, userCount());
        Customer customer = customerRepository.findById(user.getCustomerId()).orElseThrow();
        assertEquals(email, customer.getAccount().getContactInfo().getEmail());
        assertEquals(1, customerCount());
    }

    @Test
    void rejectsMissingRequiredFieldWithoutPersistingRecords() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(username, "")))
                .andExpect(status().isBadRequest());

        assertEquals(0, userCount());
        assertEquals(0, customerCount());
    }

    @Test
    void rejectsDuplicateNormalizedUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(username, "Customer")))
                .andExpect(status().isCreated());
        User original = userRepository.findByUsername(username).orElseThrow();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("  " + username.toUpperCase(Locale.ROOT) + "  ", "Customer")))
                .andExpect(status().isConflict())
                .andExpect(content().string("Username already exists"));

        assertEquals(1, userCount());
        assertEquals(1, customerCount());
        User persisted = userRepository.findByUsername(username).orElseThrow();
        assertEquals(original.getId(), persisted.getId());
        assertEquals(original.getCustomerId(), persisted.getCustomerId());
    }

    private long userCount() {
        return mongoTemplate.count(Query.query(where("username").is(username)), User.class);
    }

    private long customerCount() {
        return mongoTemplate.count(Query.query(where("account.contactInfo.email").is(email)), Customer.class);
    }

    private String request(String requestedUsername, String firstName) {
        return """
                {
                  "username": "%s",
                  "password": "Http-registration-password-123!",
                  "firstName": "%s",
                  "lastName": "Integration",
                  "email": "%s",
                  "street1": "123 Test Street",
                  "city": "Dublin",
                  "postalCode": "D02 TEST",
                  "country": "Ireland",
                  "languagePreference": "en",
                  "bannerPreference": true,
                  "linkPreference": false
                }
                """.formatted(requestedUsername, firstName, email);
    }
}
