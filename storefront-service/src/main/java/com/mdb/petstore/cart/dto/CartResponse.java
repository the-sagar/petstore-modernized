package com.mdb.petstore.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartLineResponse> items, int lineCount, BigDecimal subtotal) {

    public CartResponse {
        items = List.copyOf(items);
    }
}
