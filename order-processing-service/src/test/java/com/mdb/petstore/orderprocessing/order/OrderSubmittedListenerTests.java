package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.mdb.petstore.orderprocessing.order.messaging.OrderSubmittedListener;
import com.mdb.petstore.orderprocessing.order.model.Order;
import com.mdb.petstore.orderprocessing.order.model.OrderStatus;
import com.mdb.petstore.orderprocessing.order.repository.OrderRepository;
import com.mdb.petstore.orderprocessing.order.service.ApprovalPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderSubmittedListenerTests {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final OrderSubmittedListener listener = new OrderSubmittedListener(orders, mongo,
            new ApprovalPolicy(), new JsonMapper());

    @Test
    void databaseReadFailureEscapesForJmsRollback() {
        when(orders.findById("order-1")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThrows(DataAccessResourceFailureException.class, () -> listener.receive("{\"orderId\":\"order-1\"}"));
        verifyNoInteractions(mongo);
    }

    @Test
    void databaseWriteFailureEscapesForJmsRollback() {
        when(orders.findById("order-1")).thenReturn(Optional.of(new Order("order-1", "customer", "user",
                "test@example.com", Instant.now(), "en-US", OrderStatus.PENDING, null, null, null, List.of(),
                new BigDecimal("387.00"))));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Order.class)))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThrows(DataAccessResourceFailureException.class, () -> listener.receive("{\"orderId\":\"order-1\"}"));
    }

    @Test
    void allNonPendingStatesAreNoOps() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.PENDING) continue;
            when(orders.findById("order-1")).thenReturn(Optional.of(new Order("order-1", "customer", "user",
                    "test@example.com", Instant.now(), "en-US", status, null, null, null, List.of(), BigDecimal.ONE)));
            listener.receive("{\"orderId\":\"order-1\"}");
        }
        verifyNoInteractions(mongo);
        verify(orders, never()).save(any());
    }
}
