package com.mdb.petstore.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mdb.petstore.catalog.model.Product;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByCategoryId(String categoryId, Pageable pageable);
    // The find cursor uses a 32-bit skip; aggregation preserves large Pageable offsets.
    @org.springframework.data.mongodb.repository.Aggregation(pipeline = {
            "{'$match': {'categoryId': ?0}}", "{'$sort': {'_id': 1}}", "{'$skip': ?1}", "{'$limit': ?2}"})
    java.util.List<Product> findPageAtOffset(String parentId, long offset, int size);

    long countByCategoryId(String parentId);
}
