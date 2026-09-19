package com.mdb.petstore.admin.api;

import java.util.List;
import com.mdb.petstore.admin.client.AdminOrderClient;
import com.mdb.petstore.admin.dto.AdminOrderResponse;
import com.mdb.petstore.admin.dto.AdminOrderStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final AdminOrderClient client;
    public AdminOrderController(AdminOrderClient client) { this.client = client; }
    @GetMapping
    public List<AdminOrderResponse> list(@RequestParam(required = false) AdminOrderStatus status) { return client.list(status); }
    @GetMapping("/{orderId}")
    public AdminOrderResponse get(@PathVariable String orderId) { return client.get(orderId); }
    @PostMapping("/{orderId}/approve")
    public AdminOrderResponse approve(@PathVariable String orderId) { return client.approve(orderId); }
    @PostMapping("/{orderId}/deny")
    public AdminOrderResponse deny(@PathVariable String orderId) { return client.deny(orderId); }
}
