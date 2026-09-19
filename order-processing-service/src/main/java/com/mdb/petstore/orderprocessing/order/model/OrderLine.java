package com.mdb.petstore.orderprocessing.order.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public record OrderLine(@Positive int lineNumber, @NotBlank String categoryId, @NotBlank String productId,
        @NotBlank String itemId, @Positive int quantity,
        @NotNull @DecimalMin("0") @Field(targetType = FieldType.DECIMAL128) BigDecimal unitPrice) {
}
