package com.mdb.petstore.orderprocessing.order.model;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.PersistenceCreator;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record OrderLine(@Positive int lineNumber, @NotBlank String categoryId, @NotBlank String productId,
        @NotBlank String itemId, @Positive int quantity,
        @NotNull @DecimalMin("0") @Field(targetType = FieldType.DECIMAL128) BigDecimal unitPrice,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        Integer quantityShipped) {

    @PersistenceCreator
    public OrderLine {
        quantityShipped = quantityShipped == null ? 0 : quantityShipped;
    }

    public OrderLine(int lineNumber, String categoryId, String productId, String itemId, int quantity, BigDecimal unitPrice) {
        this(lineNumber, categoryId, productId, itemId, quantity, unitPrice, 0);
    }

    public OrderLine withQuantityShipped(int shipped) {
        return new OrderLine(lineNumber, categoryId, productId, itemId, quantity, unitPrice, shipped);
    }
}
