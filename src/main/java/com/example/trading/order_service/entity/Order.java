package com.example.trading.order_service.entity;

import com.example.trading.order_service.Enums.OrderStatus;
import com.example.trading.order_service.Enums.OrderType;
import com.example.trading.order_service.Enums.Side;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private Long quantity;
    private BigDecimal price;
    @Enumerated(EnumType.STRING) private OrderType type;
    @Enumerated(EnumType.STRING) private OrderStatus status;
    @Enumerated(EnumType.STRING) private Side side;
    private String symbol;
    private String clientId;
    private Instant createdAt;
    private Instant updatedAt;
}
