package com.mdb.petstore.identity.security;

import java.util.UUID;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutIntegrationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CustomerRepository customerRepository;

    private User testUser;
    private final String password = "Logout-integration-password-123!";

    @AfterEach
    void cleanUpTestData() {
        if (testUser != null) {
            userRepository.deleteById(testUser.getId());
            customerRepository.deleteById(testUser.getCustomerId());
        }
    }

    @Test
    void logoutInvalidatesSessionAndPreventsFurtherAccountAccess() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(get("/api/account").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(testUser.getUsername()));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(unauthenticated());

        assertTrue(session.isInvalid());
        mockMvc.perform(get("/api/account").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
        mockMvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void logoutWithoutCsrfIsRejectedAndSessionRemainsAuthenticated() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mockMvc.perform(get("/api/account").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(testUser.getUsername()));
    }

    @Test
    void logoutRequiresAuthenticationEvenWithValidCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    private MockHttpSession registerAndLogin() throws Exception {
        String username = "logout.it." + UUID.randomUUID();
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setFirstName("Logout");
        request.setLastName("Integration");
        request.setEmail(username + "@example.com");
        request.setStreet1("123 Test Street");
        request.setCity("Dublin");
        request.setPostalCode("D02 TEST");
        request.setCountry("Ireland");
        testUser = registrationService.register(request);

        var result = mockMvc.perform(post("/api/auth/login")
                        .param("username", username).param("password", password))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(username))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }
}
