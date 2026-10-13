package com.fertiplus.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConfirmedOrderConsumer {
    private final ShipmentRepository repo;
    private final ObjectMapper mapper;

    public ConfirmedOrderConsumer(ShipmentRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "orders.confirmed", groupId = "delivery-service")
    public void onConfirmed(String payload) throws Exception {
        JsonNode event = mapper.readTree(payload);
        String orderId = event.path("orderId").asText();
        if (orderId.isBlank()) return;
        repo.findByOrderId(orderId).orElseGet(() -> repo.save(new Shipment(orderId, null, "READY_FOR_FULFILLMENT")));
    }
}
