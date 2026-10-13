package com.fertiplus.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_stock", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "product_id", nullable = false, unique = true)
    public Long productId;
    @Column(nullable = false)
    public int available;

    protected Stock() {
    }

    public Stock(Long id, Long productId, int available) {
        this.id = id;
        this.productId = productId;
        this.available = available;
    }
}
