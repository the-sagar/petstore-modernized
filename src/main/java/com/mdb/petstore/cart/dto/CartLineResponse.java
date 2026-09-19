package com.mdb.petstore.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartLineResponse(String itemId, String productId, String categoryId, String productName,
        String image, String description, List<String> attributes, int quantity, BigDecimal unitPrice,
        BigDecimal lineTotal) {

    public CartLineResponse {
        attributes = List.copyOf(attributes);
    }
}
