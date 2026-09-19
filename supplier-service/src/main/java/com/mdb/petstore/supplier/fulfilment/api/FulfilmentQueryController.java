package com.mdb.petstore.supplier.fulfilment.api;

import java.util.List;
import com.mdb.petstore.supplier.fulfilment.dto.FulfilmentOrderResponse;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import com.mdb.petstore.supplier.fulfilment.service.FulfilmentQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fulfilment/orders")
public class FulfilmentQueryController {
    private final FulfilmentQueryService service;
    public FulfilmentQueryController(FulfilmentQueryService service) { this.service = service; }
    @GetMapping
    public List<FulfilmentOrderResponse> list(@RequestParam(required = false) SupplierOrder.Status status) {
        return service.list(status);
    }
    @GetMapping("/{orderId}")
    public FulfilmentOrderResponse get(@PathVariable String orderId) { return service.get(orderId); }
}
