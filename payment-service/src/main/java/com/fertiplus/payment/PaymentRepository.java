package com.fertiplus.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentRecord, String> {
    Optional<PaymentRecord> findByOrderReference(String ref);
}
