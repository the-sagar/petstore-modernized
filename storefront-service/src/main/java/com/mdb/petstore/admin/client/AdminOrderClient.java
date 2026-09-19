package com.mdb.petstore.admin.client;

import java.util.List;
import java.util.function.Supplier;
import com.mdb.petstore.admin.dto.AdminOrderResponse;
import com.mdb.petstore.admin.dto.AdminOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminOrderClient {
    private static final Logger log = LoggerFactory.getLogger(AdminOrderClient.class);
    private final RestClient client;
    public AdminOrderClient(RestClient client) { this.client = client; }

    public List<AdminOrderResponse> list(AdminOrderStatus status) {
        return call(() -> {
            var response = client.get().uri(builder -> {
                builder.path("/api/admin/orders");
                if (status != null) builder.queryParam("status", status.name());
                return builder.build();
            }).retrieve().toEntity(new ParameterizedTypeReference<List<AdminOrderResponse>>() {});
            var orders = body(response);
            orders.forEach(this::validate);
            return orders;
        });
    }

    public AdminOrderResponse get(String id) {
        return call(() -> {
            var order = body(client.get().uri("/api/admin/orders/{id}", id).retrieve().toEntity(AdminOrderResponse.class));
            validateIdentity(order, id);
            return order;
        });
    }

    public AdminOrderResponse approve(String id) { return decide(id, "approve"); }
    public AdminOrderResponse deny(String id) { return decide(id, "deny"); }

    private AdminOrderResponse decide(String id, String action) {
        return call(() -> {
            var order = body(client.post().uri("/api/admin/orders/{id}/{action}", id, action)
                    .retrieve().toEntity(AdminOrderResponse.class));
            validateIdentity(order, id);
            boolean valid = action.equals("deny") ? order.status() == AdminOrderStatus.DENIED
                    : order.status() == AdminOrderStatus.APPROVED || order.status() == AdminOrderStatus.SHIPPED_PART
                    || order.status() == AdminOrderStatus.COMPLETED;
            if (!valid) throw new RestClientException("Invalid decision response");
            log.info("Admin order decision confirmed orderId={} action={} status={}", id, action, order.status());
            return order;
        });
    }

    private <T> T body(ResponseEntity<T> response) {
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null)
            throw new RestClientException("Invalid admin response");
        return response.getBody();
    }

    private void validateIdentity(AdminOrderResponse order, String id) {
        validate(order);
        if (!id.equals(order.orderId())) throw new RestClientException("Invalid order identity");
    }

    private void validate(AdminOrderResponse order) {
        if (order == null || order.orderId() == null || order.orderId().isBlank() || order.username() == null
                || order.createdAt() == null || order.locale() == null || order.status() == null
                || order.totalPrice() == null || order.lines() == null)
            throw new RestClientException("Invalid admin response");
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
            if (status == 409) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is no longer pending");
            if (status == 400) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order management request");
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        // Remote response bodies and exception details never reach browser responses or logs.
        log.warn("Order management request could not be confirmed");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order management is temporarily unavailable");
    }
}
