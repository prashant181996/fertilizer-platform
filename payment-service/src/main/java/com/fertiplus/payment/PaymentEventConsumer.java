package com.fertiplus.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
    private final PaymentRepository repo;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafka;

    public PaymentEventConsumer(PaymentRepository repo, ObjectMapper mapper, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.mapper = mapper;
        this.kafka = kafka;
    }

    @KafkaListener(topics = "payment.requested", groupId = "payment-service")
    public void onPayment(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        String orderId = n.path("orderId").asText();
        double amount = n.path("amount").asDouble();
        String currency = n.path("currency").asText("INR");
        if (amount <= 0) throw new IllegalArgumentException("Invalid payment amount");
        PaymentRecord p = repo.findByOrderReference(orderId).orElseGet(() -> repo.saveAndFlush(new PaymentRecord(java.util.UUID.randomUUID().toString(), orderId, amount, currency, "AUTHORIZED")));
        String result = "AUTHORIZED".equals(p.status) ? "payment.authorized" : "payment.failed";
        kafka.send(result, orderId, mapper.writeValueAsString(new PaymentResult(orderId, "AUTHORIZED".equals(p.status) ? "AUTHORIZED" : "FAILED", p.id, p.amount))).get();
    }

    public record PaymentResult(String orderId, String result, String paymentId, double amount) {
    }
}
