package com.mdb.petstore.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PageControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageAndStylesheetArePublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("/api/auth/login")));
        mockMvc.perform(get("/css/petstore.css"))
                .andExpect(status().isOk());
    }

    @Test
    void registrationPageIsPublic() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("/api/auth/register")));
    }

    @Test
    void accountPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/login")));
    }

    @Test
    void authenticatedSessionCanRenderAccountWithCsrfToken() throws Exception {
        mockMvc.perform(get("/account").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("page.integration.user"))
                .andExpect(view().name("account"))
                .andExpect(content().string(containsString("/api/account")))
                .andExpect(content().string(matchesPattern("(?s).*name=\"_csrf\" content=\"[^\"]+\".*")))
                .andExpect(content().string(containsString("name=\"_csrf_header\" content=\"X-CSRF-TOKEN\"")));
    }

    @Test
    void anonymousHomeRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedHomeRedirectsToAccount() throws Exception {
        mockMvc.perform(get("/").session(authenticatedSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));
    }

    private MockHttpSession authenticatedSession() {
        // Login itself is covered by LoginIntegrationTests; these tests check page rendering only.
        var principal = User.withUsername("page.integration.user").password("unused").roles("CUSTOMER").build();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }
}
