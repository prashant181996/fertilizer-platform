package com.fertiplus.inventory;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservations", uniqueConstraints = @UniqueConstraint(name = "uk_reservation_order", columnNames = "order_id"))
public class ReservationEntity {
    @Id
    public String orderId;
    @Column(nullable = false)
    public Long productId;
    @Column(nullable = false)
    public int quantity;
    @Column(nullable = false)
    public String status;
    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    protected ReservationEntity() {
    }

    public ReservationEntity(String orderId, Long productId, int quantity, String status) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }
}
