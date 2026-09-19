package com.mdb.petstore.orderprocessing.order.repository;

import com.mdb.petstore.orderprocessing.order.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Sort;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByStatus(OrderStatus status, Sort sort);
}
