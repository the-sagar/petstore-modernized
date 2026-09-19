package com.mdb.petstore.catalog.repository;

import java.util.List;

import com.mdb.petstore.catalog.model.Item;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ItemRepository extends MongoRepository<Item, String> {

    List<Item> findByProductId(String productId);

    List<Item> findByCategoryId(String categoryId);
}
