package com.mdb.petstore.catalog.repository;

import com.mdb.petstore.catalog.model.Category;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, String> {
}
