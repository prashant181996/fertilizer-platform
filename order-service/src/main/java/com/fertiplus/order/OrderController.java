package com.fertiplus.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository repo;
    private final RestClient http;
    private final KafkaTemplate<String, String> kafka;

    public OrderController(OrderRepository r, RestClient.Builder b, KafkaTemplate<String, String> k, @Value("${services.product-url:http://localhost:8081}") String product, @Value("${services.inventory-url:http://localhost:8082}") String inventory, @Value("${services.payment-url:http://localhost:8084}") String payment) {
        repo = r;
        http = b.build();
        kafka = k;
        this.product = product;
        this.inventory = inventory;
        this.payment = payment;
    }

    private final String product, inventory, payment;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrder req, @RequestHeader(value = "Idempotency-Key", required = false) String headerKey) {
        if (req.customerId() == null || req.customerId().isBlank() || req.productId() == null || req.quantity() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId, productId and positive quantity are required");
        String key = (headerKey == null || headerKey.isBlank()) ? req.idempotencyKey() : headerKey;
        if (key == null || key.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        var existing = repo.findByIdempotencyKey(key);
        if (existing.isPresent()) return ResponseEntity.ok(existing.get());
        Map productData;
        try {
            productData = http.get().uri(product + "/api/products/{id}", req.productId()).retrieve().body(Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product service unavailable or product not found");
        }
        double price = ((Number) productData.get("price")).doubleValue();
        double total = price * req.quantity();
        try {
            http.post().uri(inventory + "/api/inventory/reservations").contentType(MediaType.APPLICATION_JSON).body(Map.of("productId", req.productId(), "quantity", req.quantity())).retrieve().toBodilessEntity();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory reservation failed: " + safeMessage(e));
        }
        try {
            Map paymentResult = http.post().uri(payment + "/api/payments/authorize").contentType(MediaType.APPLICATION_JSON).body(Map.of("orderReference", key, "amount", total, "currency", "INR")).retrieve().body(Map.class);
            String status = String.valueOf(paymentResult.getOrDefault("status", "AUTHORIZED"));
            OrderEntity saved = repo.save(new OrderEntity(req.customerId(), req.productId(), req.quantity(), total, "CONFIRMED", key));
            try {
                kafka.send("orders.created", saved.id, "{\"orderId\":\"" + saved.id + "\",\"customerId\":\"" + saved.customerId + "\",\"status\":\"CONFIRMED\"}");
            } catch (Exception ignored) {
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            try {
                http.post().uri(inventory + "/api/inventory/release").contentType(MediaType.APPLICATION_JSON).body(Map.of("productId", req.productId(), "quantity", req.quantity())).retrieve().toBodilessEntity();
            } catch (Exception ignored) {
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment authorization failed; inventory release attempted");
        }
    }

    @GetMapping
    public List<OrderEntity> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public OrderEntity get(@PathVariable String id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null ? "unavailable" : m.substring(0, Math.min(120, m.length()));
    }

    public record CreateOrder(String customerId, Long productId, int quantity, String idempotencyKey) {
    }
}
