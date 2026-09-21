package com.mdb.petstore.cart.api;

import java.util.Locale;

import com.mdb.petstore.cart.dto.AddCartItemRequest;
import com.mdb.petstore.cart.dto.CartResponse;
import com.mdb.petstore.cart.dto.UpdateCartItemRequest;
import com.mdb.petstore.cart.service.CartService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(Locale locale) {
        return cartService.getCart(locale.toLanguageTag());
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request,
            Locale locale) {
        return cartService.addItem(request.itemId(), locale.toLanguageTag());
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItemQuantity(@PathVariable String itemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            Locale locale) {
        return cartService.updateItemQuantity(itemId, request.quantity(), locale.toLanguageTag());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@PathVariable String itemId,
            Locale locale) {
        return cartService.removeItem(itemId, locale.toLanguageTag());
    }
}
