package com.mdb.petstore.cart.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(@NotNull Integer quantity) {
}
