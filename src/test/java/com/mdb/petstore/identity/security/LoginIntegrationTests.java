package com.mdb.petstore.identity.security;

import java.security.Principal;
import java.util.Locale;
import java.util.UUID;

import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;
import com.mdb.petstore.identity.service.RegistrationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LoginIntegrationTests.SessionCheckController.class)
class LoginIntegrationTests {

    private final String username = "login.it." + UUID.randomUUID();
    private final String email = username + "@example.com";
    private final String password = "Login-integration-password-123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

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
    void logsInWithNormalizedUsernameAndCreatesAuthenticatedSession() throws Exception {
        User user = registerCustomer();
        String passwordHash = user.getPasswordHash();
        assertTrue(passwordHash.startsWith("$2"));
        assertTrue(passwordEncoder.matches(password, passwordHash));

        var result = mockMvc.perform(post("/api/auth/login")
                        .param("username", "  " + username.toUpperCase(Locale.ROOT) + "  ")
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(authenticated().withUsername(username))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles", contains("ROLE_CUSTOMER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andReturn();

        assertNotNull(result.getRequest().getSession(false));
        assertEquals(passwordHash, userRepository.findByUsername(username).orElseThrow().getPasswordHash());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        registerCustomer();
        assertLoginRejected(username, "Wrong-password");
    }

    @Test
    void rejectsUnknownUsername() throws Exception {
        assertFalse(userRepository.existsByUsername(username));
        assertLoginRejected(username, password);
    }

    @Test
    void rejectsDisabledUser() throws Exception {
        User user = registerCustomer();
        user.setEnabled(false);
        userRepository.save(user);
        assertLoginRejected(username, password);
    }

    @Test
    void reusesAuthenticatedSessionForProtectedRequests() throws Exception {
        registerCustomer();
        mockMvc.perform(get("/test/security/session"))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());

        var result = mockMvc.perform(post("/api/auth/login")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(username))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/test/security/session").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(username))
                .andExpect(content().string(username));

        // The login exception does not disable CSRF for other paths.
        mockMvc.perform(post("/test/security/session").session(session))
                .andExpect(status().isForbidden());
    }

    private void assertLoginRejected(String suppliedUsername, String suppliedPassword) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", suppliedUsername)
                        .param("password", suppliedPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("\"Invalid username or password\""));
    }

    private User registerCustomer() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setFirstName("Login");
        request.setLastName("Integration");
        request.setEmail(email);
        request.setStreet1("123 Test Street");
        request.setCity("Dublin");
        request.setPostalCode("D02 TEST");
        request.setCountry("Ireland");
        return registrationService.register(request);
    }

    @RestController
    static class SessionCheckController {

        @GetMapping("/test/security/session")
        String sessionUsername(Principal principal) {
            return principal.getName();
        }
    }
}
