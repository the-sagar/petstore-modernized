package com.mdb.petstore.identity.repository;

import java.util.Optional;

import com.mdb.petstore.identity.model.User;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
