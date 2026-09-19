package com.mdb.petstore.orderprocessing.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "orders")
public record Order(@Id String id, String customerId, String username, String email, Instant createdAt,
        String locale, OrderStatus status, ContactSnapshot billingInfo, ContactSnapshot shippingInfo,
        PaymentSnapshot payment, List<OrderLine> lineItems,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal totalPrice,
        List<String> fulfilmentEventIds, @Version Long version) {

    public Order(String id, String customerId, String username, String email, Instant createdAt,
            String locale, OrderStatus status, ContactSnapshot billingInfo, ContactSnapshot shippingInfo,
            PaymentSnapshot payment, List<OrderLine> lineItems, BigDecimal totalPrice) {
        this(id, customerId, username, email, createdAt, locale, status, billingInfo, shippingInfo,
                payment, lineItems, totalPrice, List.of(), null);
    }

    @PersistenceCreator
    public Order {
        lineItems = List.copyOf(lineItems);
        fulfilmentEventIds = fulfilmentEventIds == null ? List.of() : List.copyOf(fulfilmentEventIds);
    }
}
