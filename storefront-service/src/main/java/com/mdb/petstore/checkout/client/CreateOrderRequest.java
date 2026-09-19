package com.mdb.petstore.checkout.client;

import java.math.BigDecimal;
import java.util.List;

import com.mdb.petstore.checkout.dto.ContactSnapshot;

public record CreateOrderRequest(String customerId, String username, String email, String locale,
        ContactSnapshot billingInfo, ContactSnapshot shippingInfo, Payment payment, List<Line> lineItems) {

    public CreateOrderRequest {
        lineItems = List.copyOf(lineItems);
    }

    public record Payment(String cardType, String last4) {
    }

    public record Line(int lineNumber, String categoryId, String productId, String itemId,
            int quantity, BigDecimal unitPrice) {
    }
}
