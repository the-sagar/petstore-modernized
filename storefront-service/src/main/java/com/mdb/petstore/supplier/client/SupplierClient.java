package com.mdb.petstore.supplier.client;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.mdb.petstore.supplier.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

public class SupplierClient {
    private static final Logger log = LoggerFactory.getLogger(SupplierClient.class);
    private final RestClient client;
    public SupplierClient(RestClient client) { this.client = client; }

    public List<InventoryResponse> inventory() {
        return call(() -> {
            var items = body(client.get().uri("/api/inventory").retrieve()
                    .toEntity(new ParameterizedTypeReference<List<InventoryResponse>>() {}));
            items.forEach(this::validate);
            return items;
        });
    }
    public InventoryResponse inventory(String id) {
        return call(() -> inventoryBody(client.get().uri("/api/inventory/{id}", id).retrieve()
                .toEntity(InventoryResponse.class), id));
    }
    public InventoryResponse setQuantity(String id, int quantity) {
        return call(() -> {
            var item = inventoryBody(client.put().uri("/api/inventory/{id}", id).body(Map.of("quantity", quantity))
                    .retrieve().toEntity(InventoryResponse.class), id);
            log.info("Supplier inventory set confirmed itemId={} currentQuantity={}", id, item.quantity());
            return item; // Authoritative quantity AFTER automatic pending-order allocation.
        });
    }
    public void retryPending() {
        call(() -> {
            var response = client.post().uri("/api/inventory/retry-pending").retrieve().toBodilessEntity();
            if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.NO_CONTENT)
                throw new RestClientException("Unconfirmed retry response");
            log.info("Supplier pending fulfilment retry confirmed");
            return null;
        });
    }
    public List<FulfilmentOrderResponse> orders(FulfilmentStatus status) {
        return call(() -> {
            var orders = body(client.get().uri(builder -> {
                builder.path("/api/fulfilment/orders");
                if (status != null) builder.queryParam("status", status.name());
                return builder.build();
            }).retrieve().toEntity(new ParameterizedTypeReference<List<FulfilmentOrderResponse>>() {}));
            orders.forEach(this::validate);
            return orders;
        });
    }
    public FulfilmentOrderResponse order(String id) {
        return call(() -> {
            var order = body(client.get().uri("/api/fulfilment/orders/{id}", id).retrieve().toEntity(FulfilmentOrderResponse.class));
            validate(order);
            if (!id.equals(order.orderId())) throw new RestClientException("Unexpected order identity");
            return order;
        });
    }
    private InventoryResponse inventoryBody(ResponseEntity<InventoryResponse> response, String id) {
        var item = body(response);
        validate(item);
        if (!id.equals(item.itemId())) throw new RestClientException("Unexpected item identity");
        return item;
    }
    private void validate(InventoryResponse item) {
        if (item == null || item.itemId() == null || item.itemId().isBlank() || item.quantity() == null || item.quantity() < 0 || item.updatedAt() == null)
            throw new RestClientException("Invalid inventory response");
    }
    private void validate(FulfilmentOrderResponse order) {
        if (order == null || order.orderId() == null || order.orderId().isBlank()
                || !("PENDING".equals(order.status()) || "COMPLETED".equals(order.status()))
                || order.createdAt() == null || order.updatedAt() == null || order.lines() == null || order.shipments() == null)
            throw new RestClientException("Invalid fulfilment response");
    }
    private <T> T body(ResponseEntity<T> response) {
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null)
            throw new RestClientException("Unconfirmed Supplier response");
        return response.getBody();
    }
    private <T> T call(Supplier<T> request) {
        try { return request.get(); }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier resource not found");
            if (exception.getStatusCode().value() == 400)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supplier request");
            throw unavailable();
        } catch (RestClientException exception) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        log.warn("Supplier request could not be confirmed");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Supplier service is temporarily unavailable.");
    }
}
