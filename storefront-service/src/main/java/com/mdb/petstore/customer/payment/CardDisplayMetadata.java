package com.mdb.petstore.customer.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Converts transient input to display metadata, without retaining the input. */
public final class CardDisplayMetadata {
    private CardDisplayMetadata() {}

    public static String last4(String input) {
        if (input == null || input.isBlank()) return null;
        String digits = input.replace(" ", "").replace("-", "");
        // Preserve the previous checkout format: 4–19 ASCII digits, with spaces/hyphens allowed.
        // This is format validation, not card-network or payment authorization validation.
        if (!digits.matches("[0-9]{4,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card number format");
        }
        return digits.substring(digits.length() - 4);
    }
}
