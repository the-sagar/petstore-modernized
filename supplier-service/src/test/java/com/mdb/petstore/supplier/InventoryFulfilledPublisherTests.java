package com.mdb.petstore.supplier;

import java.time.Instant;
import java.util.List;
import com.mdb.petstore.supplier.fulfilment.model.SupplierOrder;
import com.mdb.petstore.supplier.messaging.InventoryFulfilledPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;

class InventoryFulfilledPublisherTests {
    @Test
    void sendsOnlyShipmentContractAsJsonText() {
        var jms = mock(JmsTemplate.class);
        new InventoryFulfilledPublisher(jms, new JsonMapper(), "test.fulfilled").publish("order-1",
                new SupplierOrder.Shipment("event-1", List.of(new SupplierOrder.ShippedLine(1, "EST-1", 2)), true, Instant.now()));
        verify(jms).convertAndSend("test.fulfilled",
                "{\"eventId\":\"event-1\",\"orderId\":\"order-1\",\"shippedLines\":[{\"lineNumber\":1,\"itemId\":\"EST-1\",\"quantity\":2}],\"complete\":true}");
    }
}
