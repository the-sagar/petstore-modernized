package com.mdb.petstore.supplier.inventory.api;

import java.util.List;
import com.mdb.petstore.supplier.inventory.model.Inventory;
import com.mdb.petstore.supplier.inventory.service.InventoryService;
import com.mdb.petstore.supplier.fulfilment.service.SupplierFulfilmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventory;
    private final SupplierFulfilmentService fulfilment;

    public InventoryController(InventoryService inventory, SupplierFulfilmentService fulfilment) {
        this.inventory = inventory;
        this.fulfilment = fulfilment;
    }

    public record SetQuantityRequest(@NotNull @Min(0) Integer quantity) {}

    @GetMapping
    public List<Inventory> list() { return inventory.list(); }

    @GetMapping("/{itemId}")
    public Inventory get(@PathVariable String itemId) { return inventory.get(itemId); }

    @PutMapping("/{itemId}")
    public Inventory set(@PathVariable String itemId, @Valid @RequestBody SetQuantityRequest request) {
        return inventory.setQuantity(itemId, request.quantity());
    }

    @PostMapping("/retry-pending")
    public void retryPending() { fulfilment.retryPending(); }
}
