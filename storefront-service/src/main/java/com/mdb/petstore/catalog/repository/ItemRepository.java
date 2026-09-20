package com.mdb.petstore.catalog.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mdb.petstore.catalog.model.Item;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ItemRepository extends MongoRepository<Item, String> {

    Page<Item> findByProductId(String productId, Pageable pageable);

    List<Item> findByCategoryId(String categoryId);
}
