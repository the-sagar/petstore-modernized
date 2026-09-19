package com.mdb.petstore.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ItemResponse(String id, String productId, String categoryId, BigDecimal listPrice,
        BigDecimal unitCost, String image, String description, List<String> attributes) {

    public ItemResponse {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }
}
