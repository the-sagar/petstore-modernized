package com.mdb.petstore.customer.dto;

public record AccountResponse(
        String firstName,
        String lastName,
        String email,
        String phone,
        String street1,
        String street2,
        String city,
        String stateOrProvince,
        String postalCode,
        String country,
        String cardType,
        String last4,
        String expiryDate,
        String languagePreference,
        boolean bannerPreference,
        boolean linkPreference) {
}
