package com.mdb.petstore.supplier.inventory.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("inventory")
public record Inventory(@Id String itemId, int quantity, Instant updatedAt) {}
