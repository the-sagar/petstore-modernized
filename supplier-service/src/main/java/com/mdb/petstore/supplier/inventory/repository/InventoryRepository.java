package com.mdb.petstore.supplier.inventory.repository;

import com.mdb.petstore.supplier.inventory.model.Inventory;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryRepository extends MongoRepository<Inventory, String> {}
