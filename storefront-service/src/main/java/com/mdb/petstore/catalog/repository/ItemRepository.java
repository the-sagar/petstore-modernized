package com.mdb.petstore.catalog.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mdb.petstore.catalog.model.Item;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ItemRepository extends MongoRepository<Item, String> {

    Page<Item> findByProductId(String productId, Pageable pageable);

    List<Item> findByCategoryId(String categoryId);
    // The find cursor uses a 32-bit skip; aggregation preserves large Pageable offsets.
    @org.springframework.data.mongodb.repository.Aggregation(pipeline = {
            "{'$match': {'productId': ?0}}", "{'$sort': {'_id': 1}}", "{'$skip': ?1}", "{'$limit': ?2}"})
    java.util.List<Item> findPageAtOffset(String parentId, long offset, int size);

    long countByProductId(String parentId);
}
