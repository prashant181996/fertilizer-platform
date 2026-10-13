package com.fertiplus.order;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="customer_orders", uniqueConstraints=@UniqueConstraint(name="uk_order_idempotency", columnNames="idempotency_key"))
public class OrderEntity {
 @Id public String id; @Column(nullable=false) public String customerId; @Column(nullable=false) public Long productId; @Column(nullable=false) public int quantity; @Column(nullable=false) public double total; @Column(nullable=false) public String status; @Column(nullable=false,unique=true) public String idempotencyKey; @Column(nullable=false) public Instant createdAt=Instant.now(); @Column(nullable=false) public Instant updatedAt=Instant.now();
 protected OrderEntity() {}
 public OrderEntity(String customerId,Long productId,int quantity,double total,String status,String key) { this.id=UUID.randomUUID().toString(); this.customerId=customerId; this.productId=productId; this.quantity=quantity; this.total=total; this.status=status; this.idempotencyKey=key; this.createdAt=Instant.now(); this.updatedAt=this.createdAt; }
 @PreUpdate void touch(){updatedAt=Instant.now();}
}
