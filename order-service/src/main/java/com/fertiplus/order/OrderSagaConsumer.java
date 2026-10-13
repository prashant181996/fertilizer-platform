package com.fertiplus.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderSagaConsumer {
    private final OrderRepository orders;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public OrderSagaConsumer(OrderRepository orders, KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.orders = orders;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @KafkaListener(topics = {"inventory.reserved", "inventory.rejected"}, groupId = "order-inventory-saga")
    public void onInventory(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        String id = n.path("orderId").asText();
        OrderEntity o = orders.findById(id).orElse(null);
        if (o == null) return;
        String topic = payloadTopic(n);
        if ("RESERVED".equals(topic)) {
            if ("CONFIRMED".equals(o.status) || "FAILED".equals(o.status)) return;
            if (!"PAYMENT_PENDING".equals(o.status)) {
                o.status = "PAYMENT_PENDING";
                orders.save(o);
            }
            kafka.send("payment.requested", id, mapper.writeValueAsString(new PaymentRequested(id, o.total, "INR"))).get();
        } else {
            if (!"FAILED".equals(o.status)) {
                o.status = "FAILED";
                orders.save(o);
            }
        }
    }

    private String payloadTopic(JsonNode n) {
        return n.path("result").asText();
    }

    @KafkaListener(topics = {"payment.authorized", "payment.failed"}, groupId = "order-payment-saga")
    public void onPayment(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        String id = n.path("orderId").asText();
        OrderEntity o = orders.findById(id).orElse(null);
        if (o == null) return;
        if ("AUTHORIZED".equals(n.path("result").asText())) {
            if (!"CONFIRMED".equals(o.status)) {
                o.status = "CONFIRMED";
                orders.save(o);
            }
            kafka.send("orders.confirmed", id, payload).get();
        } else {
            if (!"FAILED".equals(o.status)) {
                o.status = "FAILED";
                orders.save(o);
            }
            kafka.send("inventory.release", id, mapper.writeValueAsString(new ReleaseInventory(id, o.productId, o.quantity))).get();
        }
    }

    public record PaymentRequested(String orderId, double amount, String currency) {
    }

    public record ReleaseInventory(String orderId, Long productId, int quantity) {
    }
}
