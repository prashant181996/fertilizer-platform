package com.fertiplus.delivery;

import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final Map<String, Map<String, Object>> shipments = new ConcurrentHashMap<>();

    @PostMapping
    public Map<String, Object> create(@RequestBody ShipmentRequest r) {
        return shipments.computeIfAbsent(r.orderId(), k -> Map.of("shipmentId", UUID.randomUUID().toString(), "orderId", k, "address", r.address(), "status", "CREATED"));
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> get(@PathVariable String orderId) {
        return shipments.getOrDefault(orderId, Map.of("orderId", orderId, "status", "NOT_FOUND"));
    }

    public record ShipmentRequest(String orderId, String address) {
    }
}
