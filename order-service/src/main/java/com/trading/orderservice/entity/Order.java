package com.trading.orderservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy= GenerationType.UUID)
    private String id;

    @Column(nullable=false)
    private String userId;

    @Column(nullable=false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private OrderStatus status;

    @Column(nullable=false)
    private int quantity;

    @Column(nullable=false, precision=15, scale=2)
    private BigDecimal price;

    @Column(nullable=false, precision=15, scale=2)
    private BigDecimal totalAmount;

    @Column(length = 500)
    private String failureReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime executedAt;
}
