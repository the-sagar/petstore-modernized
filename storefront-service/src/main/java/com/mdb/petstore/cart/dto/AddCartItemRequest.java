package com.mdb.petstore.cart.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCartItemRequest(@NotBlank String itemId) {
}
