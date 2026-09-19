package com.mdb.petstore.orderprocessing.order.api;

import java.net.URI;

import com.mdb.petstore.orderprocessing.order.dto.CreateOrderRequest;
import com.mdb.petstore.orderprocessing.order.dto.OrderResponse;
import com.mdb.petstore.orderprocessing.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        var response = orderService.create(request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.orderId())).body(response);
    }
}
