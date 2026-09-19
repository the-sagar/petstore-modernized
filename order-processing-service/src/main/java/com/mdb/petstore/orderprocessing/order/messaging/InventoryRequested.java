package com.mdb.petstore.orderprocessing.order.messaging;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record InventoryRequested(@NotBlank String orderId, @NotEmpty List<@NotNull @Valid Line> lines) {
    public record Line(@Positive int lineNumber, @NotBlank String itemId, @Positive int quantity) {}
}
