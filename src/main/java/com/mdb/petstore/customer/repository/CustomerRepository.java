package com.mdb.petstore.customer.repository;

import com.mdb.petstore.customer.model.Customer;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerRepository extends MongoRepository<Customer, String> {
}
