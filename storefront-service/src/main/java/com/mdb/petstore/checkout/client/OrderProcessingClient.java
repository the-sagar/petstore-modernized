package com.mdb.petstore.checkout.client;

import java.math.BigDecimal;

import com.mdb.petstore.checkout.dto.OrderResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderProcessingClient {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingClient.class);
    private final RestClient restClient;

    public OrderProcessingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public OrderResponse create(CreateOrderRequest request) {
        try {
            var response = restClient.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().toEntity(OrderResponse.class);
            OrderResponse order = response.getBody();
            BigDecimal expectedTotal = request.lineItems().stream()
                    .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (response.getStatusCode() != HttpStatus.CREATED || order == null
                    || order.orderId() == null || order.orderId().isBlank()
                    || !"PENDING".equals(order.status()) || order.createdAt() == null
                    || order.totalPrice() == null || order.totalPrice().compareTo(expectedTotal) != 0) {
                throw new RestClientException("Invalid order creation response");
            }
            return order;
        } catch (RestClientException exception) {
            // Do not propagate or log remote bodies, which may contain sensitive data.
            log.warn("Order creation call failed customerId={} lineCount={}",
                    request.customerId(), request.lineItems().size());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order creation could not be confirmed");
        }
    }
}
