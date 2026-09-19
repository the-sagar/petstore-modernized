package com.mdb.petstore.catalog.repository;

import java.util.List;

import com.mdb.petstore.catalog.model.Product;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByCategoryId(String categoryId);
}
