package com.mdb.petstore.identity.service;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@SpringBootTest
class RegistrationServiceRollbackIntegrationTests {

    private final String username = "registration.integration.rollback." + UUID.randomUUID();
    private final String email = username + "@example.com";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean(enforceOverride = true)
    private UserRepository userRepository;

    @AfterEach
    void cleanUpTestData() {
        // Runs after rollback assertions, including if the test detects a regression.
        mongoTemplate.remove(Query.query(where("account.contactInfo.email").is(email)), Customer.class);
    }

    @Test
    void rollsBackCustomerWhenUserSaveFails() {
        assertEquals(0, mongoTemplate.count(
                Query.query(where("account.contactInfo.email").is(email)), Customer.class));
        RuntimeException failure = new RuntimeException("Simulated user persistence failure");
        AtomicReference<String> customerId = new AtomicReference<>();
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            customerId.set(user.getCustomerId());
            assertNotNull(customerId.get());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            // This real read joins the service transaction and proves the first save occurred.
            Customer customer = customerRepository.findById(customerId.get()).orElseThrow();
            assertEquals(email, customer.getAccount().getContactInfo().getEmail());
            throw failure;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> registrationService.register(request()));

        assertSame(failure, thrown);
        verify(userRepository).existsByUsername(username);
        verify(userRepository).save(any(User.class));
        assertNotNull(customerId.get());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        // Outside the service transaction, the customer must be absent before cleanup.
        assertTrue(customerRepository.findById(customerId.get()).isEmpty());
        assertEquals(0, mongoTemplate.count(
                Query.query(where("account.contactInfo.email").is(email)), Customer.class));
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
