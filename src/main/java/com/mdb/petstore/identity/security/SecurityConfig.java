package com.mdb.petstore.identity.security;

import java.util.Set;
import java.util.stream.Collectors;

import com.mdb.petstore.identity.dto.LoginResponse;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            MongoUserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        http.authenticationProvider(provider);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/", "/login", "/register", "/css/**", "/assets/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .anyRequest().authenticated());
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/register", "/api/auth/login"));
        var accountMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/account");
        var logoutMatcher = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout");
        var loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
            if (accountMatcher.matches(request) || logoutMatcher.matches(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                loginEntryPoint.commence(request, response, exception);
            }
        }));
        http.formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    Set<String> roles = authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(authority -> authority.startsWith("ROLE_"))
                            .collect(Collectors.toSet());
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(),
                            new LoginResponse(authentication.getName(), roles));
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), "Invalid username or password");
                }));
        var trustResolver = new AuthenticationTrustResolverImpl();
        http.logout(logout -> logout
                // LogoutFilter runs before authorization, so require authentication in its matcher.
                .logoutRequestMatcher(request -> logoutMatcher.matches(request)
                        && trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication()))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
