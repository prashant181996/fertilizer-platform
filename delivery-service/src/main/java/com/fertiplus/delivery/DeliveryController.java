package com.fertiplus.delivery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final ShipmentRepository repo;

    public DeliveryController(ShipmentRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public Shipment create(@RequestBody ShipmentRequest request) {
        if (request.orderId() == null || request.orderId().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        return repo.findByOrderId(request.orderId()).orElseGet(() -> repo.save(new Shipment(request.orderId(), request.address(), "CREATED")));
    }

    @GetMapping("/{orderId}")
    public Shipment get(@PathVariable String orderId) {
        return repo.findByOrderId(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
    }

    public record ShipmentRequest(String orderId, String address) {
    }
}
