package com.mdb.petstore.web.i18n;

import java.util.Locale;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Component("localeResolver")
public class CustomerLocaleResolver extends SessionLocaleResolver {
    private static final String RESOLVED = CustomerLocaleResolver.class.getName() + ".resolved";
    private final UserRepository users;
    private final CustomerRepository customers;

    public CustomerLocaleResolver(UserRepository users, CustomerRepository customers) {
        this.users = users;
        this.customers = customers;
        setDefaultLocale(Locale.US);
    }

    public static boolean operational(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/admin/")
                || request.getRequestURI().startsWith(request.getContextPath() + "/supplier/");
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return resolveLocaleContext(request).getLocale();
    }

    @Override
    public LocaleContext resolveLocaleContext(HttpServletRequest request) {
        if (operational(request)) return new SimpleLocaleContext(Locale.US);
        Locale resolved = (Locale) request.getAttribute(RESOLVED);
        if (resolved == null) {
            // Explicit query > persisted customer preference > session > English.
            // Navigation retains explicit choices in URLs without modifying the profile.
            String explicit = request.getParameter("locale");
            if (explicit != null) {
                resolved = SupportedLocales.resolve(explicit);
                super.setLocale(request, null, resolved);
            } else {
                resolved = preferred(SecurityContextHolder.getContext().getAuthentication());
                if (resolved == null) resolved = super.resolveLocaleContext(request).getLocale();
                resolved = SupportedLocales.resolve(resolved == null ? null : resolved.toLanguageTag());
            }
            request.setAttribute(RESOLVED, resolved);
        }
        return new SimpleLocaleContext(resolved);
    }

    public void applyLoginPreference(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        Locale preference = preferred(authentication);
        if (preference != null) super.setLocale(request, response, preference);
    }

    private Locale preferred(Authentication authentication) {
        // Operational users have no Customer aggregate; never look one up for those identities.
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) return null;
        return users.findByUsername(authentication.getName()).filter(u -> u.getCustomerId() != null)
                .flatMap(u -> customers.findById(u.getCustomerId()))
                .map(c -> SupportedLocales.resolve(c.getProfile() == null ? null : c.getProfile().getLanguagePreference()))
                .orElse(null);
    }
}
