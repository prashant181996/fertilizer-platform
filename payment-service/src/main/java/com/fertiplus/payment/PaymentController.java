package com.fertiplus.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final Map<String, Map<String, Object>> records = new ConcurrentHashMap<>();

    @PostMapping("/authorize")
    public Map<String, Object> authorize(@RequestBody PaymentRequest r) {
        if (r.amount() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        return records.computeIfAbsent(r.orderReference(), k -> Map.of("paymentId", UUID.randomUUID().toString(), "orderReference", k, "amount", r.amount(), "currency", r.currency() == null ? "INR" : r.currency(), "status", "AUTHORIZED"));
    }

    @GetMapping("/{reference}")
    public Map<String, Object> get(@PathVariable String reference) {
        var p = records.get(reference);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        return p;
    }

    public record PaymentRequest(String orderReference, double amount, String currency) {
    }
}
