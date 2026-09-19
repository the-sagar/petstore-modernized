package com.mdb.petstore.orderprocessing.order.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaymentSnapshot(@NotBlank String cardType, @NotBlank @Pattern(regexp = "[0-9]{4}") String last4) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected payment field");
    }
}
