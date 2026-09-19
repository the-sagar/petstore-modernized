package com.mdb.petstore.orderprocessing.order.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactSnapshot(
        @NotBlank String firstName, @NotBlank String lastName,
        @NotBlank @Email String email, String phone,
        @NotBlank String street1, String street2, @NotBlank String city,
        String stateOrProvince, @NotBlank String postalCode, @NotBlank String country) {
}
