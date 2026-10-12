package com.fertiplus.product;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true)
    public String sku;
    @Column(nullable = false)
    public String name;
    @Column(nullable = false)
    public double price;
    @Column(nullable = false)
    public boolean active;

    protected Product() {
    }

    public Product(Long id, String sku, String name, double price, boolean active) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.active = active;
    }
}
