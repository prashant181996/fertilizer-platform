package com.fertiplus.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final StockRepository repo;

    public InventoryController(StockRepository r) {
        repo = r;
    }

    @GetMapping("/{productId}")
    public Map<String, Object> stock(@PathVariable Long productId) {
        Stock s = repo.findByProductId(productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));
        return Map.of("productId", productId, "available", s.available);
    }

    @PostMapping("/reservations")
    @Transactional
    public Map<String, Object> reserve(@RequestBody Reservation r) {
        if (r.quantity() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        Stock s = repo.findLockedByProductId(r.productId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));
        if (s.available < r.quantity()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
        s.available -= r.quantity();
        repo.save(s);
        return Map.of("productId", r.productId(), "quantityReserved", r.quantity(), "available", s.available, "status", "RESERVED");
    }

    @PostMapping("/release")
    @Transactional
    public Map<String, Object> release(@RequestBody Reservation r) {
        Stock s = repo.findLockedByProductId(r.productId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));
        s.available += r.quantity();
        repo.save(s);
        return Map.of("productId", r.productId(), "available", s.available, "status", "RELEASED");
    }

    public record Reservation(Long productId, int quantity) {
    }
}
