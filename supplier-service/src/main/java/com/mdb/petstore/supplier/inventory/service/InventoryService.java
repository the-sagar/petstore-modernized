package com.mdb.petstore.supplier.inventory.service;

import java.time.Instant;
import java.util.List;
import com.mdb.petstore.supplier.inventory.model.Inventory;
import com.mdb.petstore.supplier.inventory.repository.InventoryRepository;
import com.mdb.petstore.supplier.fulfilment.service.SupplierFulfilmentService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryRepository inventory;
    private final MongoTemplate mongo;
    private final SupplierFulfilmentService fulfilment;

    public InventoryService(InventoryRepository inventory, MongoTemplate mongo, SupplierFulfilmentService fulfilment) {
        this.inventory = inventory;
        this.mongo = mongo;
        this.fulfilment = fulfilment;
    }

    public List<Inventory> list() { return inventory.findAll(Sort.by("itemId")); }

    public Inventory get(String itemId) {
        return inventory.findById(itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown inventory item"));
    }

    public Inventory setQuantity(String itemId, int quantity) {
        if (quantity < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be nonnegative");
        var updated = mongo.findAndModify(Query.query(Criteria.where("_id").is(itemId)),
                Update.update("quantity", quantity).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Inventory.class);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown inventory item");
        log.info("Inventory quantity set itemId={} quantity={}", itemId, quantity);
        if (quantity > 0) fulfilment.retryPending();
        // Return current stock after pending orders have consumed their allocations.
        return get(itemId);
    }
}
