package com.fertiplus.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final StockRepository repo;

    public InventoryController(StockRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{productId}")
    public Map<String, Object> stock(@PathVariable Long productId) {
        Stock s = repo.findByProductId(productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found"));
        return Map.of("productId", productId, "available", s.available);
    }
}
