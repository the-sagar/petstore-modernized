package com.mdb.petstore.supplier.fulfilment.repository;

import java.util.List;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupplierOrderRepository extends MongoRepository<SupplierOrder, String> {
    List<SupplierOrder> findByStatusOrderByCreatedAtAscOrderIdAsc(SupplierOrder.Status status);
}
