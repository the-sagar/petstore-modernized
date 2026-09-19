package com.mdb.petstore.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private static final Logger log = LoggerFactory.getLogger(PageController.class);

    @GetMapping("/")
    public String home() {
        return "redirect:/shop";
    }

    @GetMapping("/shop")
    public String shop() {
        return "shop";
    }

    @GetMapping("/shop/categories/{categoryId}")
    public String category() {
        return "category";
    }

    @GetMapping("/shop/products/{productId}")
    public String product() {
        return "product";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "checkout";
    }

    @GetMapping("/login")
    public String login() {
        log.debug("Login page requested");
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        log.debug("Registration page requested");
        return "register";
    }

    @GetMapping("/account")
    public String account() {
        log.debug("Account page requested");
        return "account";
    }
}
