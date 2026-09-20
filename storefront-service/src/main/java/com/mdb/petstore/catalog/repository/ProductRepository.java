package com.mdb.petstore.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mdb.petstore.catalog.model.Product;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByCategoryId(String categoryId, Pageable pageable);
}
