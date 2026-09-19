package com.mdb.petstore.supplier.dto;

import java.time.Instant;

public record InventoryResponse(String itemId, Integer quantity, Instant updatedAt) {}
