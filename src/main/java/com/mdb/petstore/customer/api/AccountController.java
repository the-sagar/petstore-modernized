package com.mdb.petstore.customer.api;

import java.security.Principal;

import com.mdb.petstore.customer.dto.AccountResponse;
import com.mdb.petstore.customer.dto.UpdateAccountRequest;
import com.mdb.petstore.customer.service.AccountService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountResponse getAccount(Principal principal) {
        log.debug("GET /api/account invoked for username={}", principal.getName());
        return accountService.getAccount(principal.getName());
    }

    @PutMapping
    public AccountResponse updateAccount(Principal principal, @Valid @RequestBody UpdateAccountRequest request) {
        log.debug("PUT /api/account invoked for username={}", principal.getName());
        return accountService.updateAccount(principal.getName(), request);
    }
}
