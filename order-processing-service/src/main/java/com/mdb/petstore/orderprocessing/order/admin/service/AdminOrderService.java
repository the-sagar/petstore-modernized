package com.mdb.petstore.orderprocessing.order.admin.service;

import java.util.List;
import com.mdb.petstore.orderprocessing.order.admin.dto.AdminOrderResponse;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import com.mdb.petstore.orderprocessing.order.service.OrderApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOrderService {
    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);
    private static final Sort ORDERING = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private final OrderRepository orders;
    private final OrderApprovalService approval;
    private final MongoTemplate mongo;

    public AdminOrderService(OrderRepository orders, OrderApprovalService approval, MongoTemplate mongo) {
        this.orders = orders;
        this.approval = approval;
        this.mongo = mongo;
    }

    public List<AdminOrderResponse> list(OrderStatus status) {
        return (status == null ? orders.findAll(ORDERING) : orders.findByStatus(status, ORDERING))
                .stream().map(this::response).toList();
    }

    public AdminOrderResponse get(String id) { return response(requireOrder(id)); }

    public AdminOrderResponse approve(String id) {
        var order = requireOrder(id);
        if (order.status() != OrderStatus.PENDING || !approval.approve(order)) throw conflict();
        log.info("Manual order approval accepted orderId={}", id);
        // Supplier may already have advanced the order; return authoritative current state.
        return get(id);
    }

    public AdminOrderResponse deny(String id) {
        requireOrder(id);
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(OrderStatus.PENDING)),
                Update.update("status", OrderStatus.DENIED), Order.class);
        if (result.getModifiedCount() != 1) throw conflict();
        log.info("Order manually denied orderId={}", id);
        return get(id);
    }

    private Order requireOrder(String id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Order is no longer pending");
    }

    private AdminOrderResponse response(Order order) {
        var lines = order.lineItems().stream().map(line -> new AdminOrderResponse.Line(line.lineNumber(),
                line.itemId(), line.productId(), line.categoryId(), line.quantity(), line.quantityShipped(), line.unitPrice())).toList();
        return new AdminOrderResponse(order.id(), order.username(), order.createdAt(), order.locale(),
                order.status(), order.totalPrice(), lines);
    }
}
