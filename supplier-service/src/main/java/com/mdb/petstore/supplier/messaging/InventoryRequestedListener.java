package com.mdb.petstore.supplier.messaging;

import java.util.HashSet;
import com.mdb.petstore.supplier.fulfilment.service.SupplierFulfilmentService;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryRequestedListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryRequestedListener.class);
    private final ObjectMapper mapper;
    private final Validator validator;
    private final SupplierFulfilmentService service;

    public InventoryRequestedListener(ObjectMapper mapper, Validator validator, SupplierFulfilmentService service) {
        this.mapper = mapper;
        this.validator = validator;
        this.service = service;
    }

    @JmsListener(destination = "${petstore.inventory.requested-destination}")
    public void receive(String json) {
        InventoryRequested event;
        try {
            event = json == null ? null : mapper.readValue(json, InventoryRequested.class);
        } catch (JacksonException exception) {
            log.warn("Malformed InventoryRequested ignored");
            return;
        }
        if (event == null || !validator.validate(event).isEmpty()) {
            log.warn("Invalid InventoryRequested ignored");
            return;
        }
        var numbers = new HashSet<Integer>();
        if (event.lines().stream().anyMatch(l -> !numbers.add(l.lineNumber()))) {
            log.warn("Duplicate line numbers ignored orderId={}", event.orderId());
            return;
        }
        log.info("InventoryRequested received orderId={} lineCount={}", event.orderId(), event.lines().size());
        service.receive(event);
    }
}
