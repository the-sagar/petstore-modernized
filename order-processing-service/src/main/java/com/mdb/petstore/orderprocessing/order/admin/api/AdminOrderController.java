package com.mdb.petstore.orderprocessing.order.admin.api;

import java.util.List;
import com.mdb.petstore.orderprocessing.order.admin.dto.AdminOrderResponse;
import com.mdb.petstore.orderprocessing.order.admin.service.AdminOrderService;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderService service;
    public AdminOrderController(AdminOrderService service) { this.service = service; }

    @GetMapping
    public List<AdminOrderResponse> list(@RequestParam(required = false) OrderStatus status) { return service.list(status); }
    @GetMapping("/{orderId}")
    public AdminOrderResponse get(@PathVariable String orderId) { return service.get(orderId); }
    @PostMapping("/{orderId}/approve")
    public AdminOrderResponse approve(@PathVariable String orderId) { return service.approve(orderId); }
    @PostMapping("/{orderId}/deny")
    public AdminOrderResponse deny(@PathVariable String orderId) { return service.deny(orderId); }
}
