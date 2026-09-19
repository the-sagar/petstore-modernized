package com.mdb.petstore.orderprocessing.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "orders")
public record Order(@Id String id, String customerId, String username, String email, Instant createdAt,
        String locale, OrderStatus status, ContactSnapshot billingInfo, ContactSnapshot shippingInfo,
        PaymentSnapshot payment, List<OrderLine> lineItems,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal totalPrice) {

    public Order {
        lineItems = List.copyOf(lineItems);
    }
}
