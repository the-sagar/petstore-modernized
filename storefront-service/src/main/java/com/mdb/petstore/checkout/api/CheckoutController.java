package com.mdb.petstore.checkout.api;

import java.security.Principal;

import com.mdb.petstore.checkout.dto.CheckoutRequest;
import com.mdb.petstore.checkout.dto.OrderResponse;
import com.mdb.petstore.checkout.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(Principal principal, @Valid @RequestBody CheckoutRequest request,
            @RequestParam(defaultValue = "en-US") String locale) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checkoutService.checkout(principal.getName(), request, locale));
    }
}
