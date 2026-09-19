package com.mdb.petstore.supplier.api;

import java.util.List;
import com.mdb.petstore.supplier.client.SupplierClient;
import com.mdb.petstore.supplier.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/supplier")
public class SupplierController {
    private final SupplierClient client;
    public SupplierController(SupplierClient client) { this.client = client; }
    public record SetQuantityRequest(@NotNull @Min(0) Integer quantity) {}
    @GetMapping("/inventory")
    public List<InventoryResponse> inventory() { return client.inventory(); }
    @GetMapping("/inventory/{itemId}")
    public InventoryResponse inventory(@PathVariable String itemId) { return client.inventory(itemId); }
    @PutMapping("/inventory/{itemId}")
    public InventoryResponse set(@PathVariable String itemId, @Valid @RequestBody SetQuantityRequest request) {
        return client.setQuantity(itemId, request.quantity());
    }
    @PostMapping("/inventory/retry-pending")
    public void retryPending() { client.retryPending(); }
    @GetMapping("/orders")
    public List<FulfilmentOrderResponse> orders(@RequestParam(required = false) FulfilmentStatus status) { return client.orders(status); }
    @GetMapping("/orders/{orderId}")
    public FulfilmentOrderResponse order(@PathVariable String orderId) { return client.order(orderId); }
}
