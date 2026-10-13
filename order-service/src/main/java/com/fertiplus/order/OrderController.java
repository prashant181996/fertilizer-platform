package com.fertiplus.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final OrderRepository repo;

    public OrderController(OrderService service, OrderRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateOrder req, @RequestHeader(value = "Idempotency-Key", required = false) String headerKey) {
        String key = (headerKey == null || headerKey.isBlank()) ? req.idempotencyKey() : headerKey;
        if (key == null || key.isBlank() || key.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        OrderEntity order = service.create(req, key);
        return ResponseEntity.accepted().location(URI.create("/api/orders/" + order.id)).body(Map.of("orderId", order.id, "status", order.status, "message", "Order accepted for asynchronous processing"));
    }

    @GetMapping
    public List<OrderEntity> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public OrderEntity get(@PathVariable String id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    public record CreateOrder(@NotBlank String customerId, @NotNull Long productId, @Min(1) int quantity,
                              String idempotencyKey) {
    }
}
