package com.mdb.petstore.orderprocessing.order.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mdb.petstore.orderprocessing.order.model.ContactSnapshot;
import com.mdb.petstore.orderprocessing.order.model.OrderLine;
import com.mdb.petstore.orderprocessing.order.model.PaymentSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(@NotBlank String customerId, @NotBlank String username,
        @NotBlank @Email String email, @NotBlank String locale,
        @NotNull @Valid ContactSnapshot billingInfo, @NotNull @Valid ContactSnapshot shippingInfo,
        @NotNull @Valid PaymentSnapshot payment, @NotEmpty List<@NotNull @Valid OrderLine> lineItems) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected order field");
    }
}
