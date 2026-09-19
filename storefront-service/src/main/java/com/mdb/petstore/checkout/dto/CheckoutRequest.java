package com.mdb.petstore.checkout.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull @Valid ContactSnapshot billingInfo,
        @NotNull @Valid ContactSnapshot shippingInfo) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected checkout field");
    }
}
