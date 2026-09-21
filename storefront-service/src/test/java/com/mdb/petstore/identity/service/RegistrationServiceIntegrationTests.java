package com.mdb.petstore.identity.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.model.Role;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@SpringBootTest
class RegistrationServiceIntegrationTests {

    // A fresh marker isolates this run from existing data and concurrent runs.
    private final String username = "registration.integration.user." + UUID.randomUUID();
    private final String email = username + "@example.com";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUpTestData() {
        mongoTemplate.remove(Query.query(where("username").is(username)), User.class);
        mongoTemplate.remove(Query.query(where("account.contactInfo.email").is(email)), Customer.class);
    }

    @Test
    void registersCustomerAndUser() {
        assertFalse(userRepository.existsByUsername(username));
        assertEquals(0, mongoTemplate.count(
                Query.query(where("account.contactInfo.email").is(email)), Customer.class));
        RegisterRequest request = request();

        User returned = registrationService.register(request);

        assertNotNull(returned.getId());
        assertEquals(username, returned.getUsername());
        assertEquals(Set.of(Role.CUSTOMER), returned.getRoles());
        assertTrue(returned.isEnabled());
        assertNotNull(returned.getCustomerId());
        assertNotEquals(request.getPassword(), returned.getPasswordHash());
        assertTrue(passwordEncoder.matches(request.getPassword(), returned.getPasswordHash()));

        var users = mongoTemplate.find(Query.query(where("username").is(username)), User.class);
        assertEquals(1, users.size());
        User persisted = users.getFirst();
        assertEquals(returned.getId(), persisted.getId());
        assertEquals(username, persisted.getUsername());
        assertEquals(Set.of(Role.CUSTOMER), persisted.getRoles());
        assertTrue(persisted.isEnabled());
        assertEquals(returned.getCustomerId(), persisted.getCustomerId());
        assertEquals(returned.getPasswordHash(), persisted.getPasswordHash());

        Customer customer = customerRepository.findById(persisted.getCustomerId()).orElseThrow();
        assertEquals(1, mongoTemplate.count(
                Query.query(where("account.contactInfo.email").is(email)), Customer.class));
        var contact = customer.getAccount().getContactInfo();
        assertEquals(request.getFirstName(), contact.getFirstName());
        assertEquals(request.getLastName(), contact.getLastName());
        assertEquals(email, contact.getEmail());
        assertEquals(request.getPhone(), contact.getPhone());

        var address = contact.getAddress();
        assertEquals(request.getStreet1(), address.getStreet1());
        assertEquals(request.getStreet2(), address.getStreet2());
        assertEquals(request.getCity(), address.getCity());
        assertEquals(request.getStateOrProvince(), address.getStateOrProvince());
        assertEquals(request.getPostalCode(), address.getPostalCode());
        assertEquals(request.getCountry(), address.getCountry());

        var card = customer.getAccount().getCreditCard();
        assertEquals(request.getCardType(), card.getCardType());
        assertEquals("1111", card.getLast4());
        var raw = mongoTemplate.getCollection("customers").find(new org.bson.Document("account.contactInfo.email", email)).first();
        assertNotNull(raw);
        assertFalse(raw.toJson().contains("cardNumber"));
        assertFalse(raw.toJson().contains("4111111111111111"));
        assertEquals(request.getExpiryDate(), card.getExpiryDate());

        var profile = customer.getProfile();
        assertEquals("en-US", profile.getLanguagePreference());
        assertEquals(request.isBannerPreference(), profile.isBannerPreference());
        assertEquals(request.isLinkPreference(), profile.isLinkPreference());

        assertNotNull(persisted.getCreatedAt());
        assertEquals(persisted.getCreatedAt(), persisted.getUpdatedAt());
        assertEquals(persisted.getCreatedAt(), customer.getCreatedAt());
        assertEquals(persisted.getCreatedAt(), customer.getUpdatedAt());
    }

    private RegisterRequest request() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("  " + username.toUpperCase(Locale.ROOT) + "  ");
        request.setPassword("Integration-password-123!");
        request.setFirstName("Integration");
        request.setLastName("Customer");
        request.setEmail(email);
        request.setPhone("555-0100");
        request.setStreet1("123 Test Street");
        request.setStreet2("Unit 2");
        request.setCity("Dublin");
        request.setStateOrProvince("Dublin");
        request.setPostalCode("D02 TEST");
        request.setCountry("Ireland");
        request.setCardType("VISA");
        request.setCardNumber("4111111111111111");
        request.setExpiryDate("12/2030");
        request.setLanguagePreference("en");
        request.setBannerPreference(true);
        request.setLinkPreference(false);
        return request;
    }
}
