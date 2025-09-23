package com.example.trading.order_service.entity;

import com.example.trading.order_service.Enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "ix_orders_user_time", columnList = "user_id, placed_at DESC"),
                @Index(name = "ix_orders_instr_time", columnList = "instrument_id, placed_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_orders_client_order", columnNames = {"user_id", "client_order_id"})
        }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id", nullable = false, length = 64)
    private String instrumentId;

    @Column(name = "instrument_symbol", length = 32)
    private String instrumentSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 8)
    private OrderSide orderSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private OrderType type = OrderType.MARKET;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal totalQuantity;

    @Column(name = "filled_quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(name = "avg_fill_price", precision = 18, scale = 8)
    private BigDecimal avgFillPrice;

    @Column(name = "notional_value", precision = 20, scale = 8)
    private BigDecimal notionalValue; // Sum of fill qty*price

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, length = 8)
    private TimeInForce timeInForce = TimeInForce.IMMEDIATE_OR_CANCEL;

    @Column(name = "client_order_id", length = 64)
    private String clientOrderId; // Idempotency key

    @Column(name = "placed_at", nullable = false)
    private OffsetDateTime placedAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "advanced_features", length = 18)
    private AdvancedFeatures advancedFeatures = AdvancedFeatures.NULL;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Executions> items = new ArrayList<>();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
