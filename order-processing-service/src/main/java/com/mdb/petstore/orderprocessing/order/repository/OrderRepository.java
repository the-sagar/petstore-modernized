package com.mdb.petstore.orderprocessing.order.repository;

import com.mdb.petstore.orderprocessing.order.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {
}
