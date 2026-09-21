package com.mdb.petstore.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationAdvice {
    @ModelAttribute("effectiveLocale")
    public String effectiveLocale(java.util.Locale locale) { return locale.toLanguageTag(); }

    @ModelAttribute("operationalPage")
    public boolean operationalPage(jakarta.servlet.http.HttpServletRequest request) {
        return com.mdb.petstore.web.i18n.CustomerLocaleResolver.operational(request);
    }

    @ModelAttribute("isSupplier")
    public boolean isSupplier(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPPLIER"));
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
