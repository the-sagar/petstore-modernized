package com.mdb.petstore.supplier.messaging;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record InventoryFulfilled(@NotBlank String eventId, @NotBlank String orderId,
        @NotEmpty List<@NotNull @Valid Line> shippedLines, boolean complete) {
    public record Line(@Positive int lineNumber, @NotBlank String itemId, @Positive int quantity) {}
}
