package com.mdb.petstore.cart.service;

import java.math.BigDecimal;
import java.util.ArrayList;

import com.mdb.petstore.cart.dto.CartLineResponse;
import com.mdb.petstore.cart.dto.CartResponse;
import com.mdb.petstore.cart.model.ShoppingCart;
import com.mdb.petstore.catalog.service.CatalogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final ShoppingCart shoppingCart;
    private final CatalogService catalogService;

    public CartService(ShoppingCart shoppingCart, CatalogService catalogService) {
        this.shoppingCart = shoppingCart;
        this.catalogService = catalogService;
    }

    public CartResponse getCart(String locale) {
        var lines = new ArrayList<CartLineResponse>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var entry : shoppingCart.getQuantities().entrySet()) {
            var item = catalogService.getItem(entry.getKey(), locale);
            var product = catalogService.getProduct(item.productId(), locale);
            BigDecimal unitPrice = item.listPrice();
            requirePrice(unitPrice);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));
            lines.add(new CartLineResponse(item.id(), item.productId(), item.categoryId(), product.name(),
                    item.image(), item.description(), item.attributes(), entry.getValue(), unitPrice, lineTotal));
            subtotal = subtotal.add(lineTotal);
        }
        return new CartResponse(lines, lines.size(), subtotal);
    }

    public CartResponse addItem(String itemId, String locale) {
        requirePrice(catalogService.getItem(itemId, locale).listPrice());
        shoppingCart.setQuantity(itemId, 1);
        log.info("Cart item added itemId={} quantity=1", itemId);
        return getCart(locale);
    }

    public CartResponse updateItemQuantity(String itemId, int quantity, String locale) {
        // Validate before any mutation, including removal via a nonpositive quantity.
        var item = catalogService.getItem(itemId, locale);
        if (quantity > 0) requirePrice(item.listPrice());
        shoppingCart.setQuantity(itemId, quantity);
        if (quantity <= 0) {
            log.info("Cart item removed itemId={}", itemId);
        } else {
            log.info("Cart quantity changed itemId={} quantity={}", itemId, quantity);
        }
        return getCart(locale);
    }

    private static void requirePrice(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Item price unavailable");
        }
    }

    public CartResponse removeItem(String itemId, String locale) {
        shoppingCart.removeItem(itemId);
        log.info("Cart item removed itemId={}", itemId);
        return getCart(locale);
    }
}
